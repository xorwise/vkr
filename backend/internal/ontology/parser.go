package ontology

import (
	"encoding/xml"
	"fmt"
	"io"
	"os"
	"strings"
)

// --- XML structures for OWL parsing ---

type owlOntology struct {
	XMLName             xml.Name               `xml:"Ontology"`
	ClassAssertions     []owlClassAssertion    `xml:"ClassAssertion"`
	ObjPropertyAsserts  []owlObjPropAssertion  `xml:"ObjectPropertyAssertion"`
	DataPropertyAsserts []owlDataPropAssertion `xml:"DataPropertyAssertion"`
}

type owlClassAssertion struct {
	Class           owlIRI `xml:"Class"`
	NamedIndividual owlIRI `xml:"NamedIndividual"`
}

type owlObjPropAssertion struct {
	ObjectProperty owlIRI `xml:"ObjectProperty"`
	Subject        owlIRI `xml:"NamedIndividual"`
	// second NamedIndividual is the object — we need raw elements
}

type owlDataPropAssertion struct {
	DataProperty    owlIRI `xml:"DataProperty"`
	NamedIndividual owlIRI `xml:"NamedIndividual"`
	Literal         string `xml:"Literal"`
}

type owlIRI struct {
	IRI string `xml:"IRI,attr"`
}

// rawObjPropAssertion keeps both subject and object IRIs
type rawObjPropAssertion struct {
	Property string
	Subject  string
	Object   string
}

// ParseFile reads and parses an OWL/XML file into Ontology
func ParseFile(path string) (*Ontology, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, fmt.Errorf("open OWL file: %w", err)
	}
	defer f.Close()
	return Parse(f)
}

// Parse reads OWL/XML from a reader
func Parse(r io.Reader) (*Ontology, error) {
	data, err := io.ReadAll(r)
	if err != nil {
		return nil, err
	}

	// We parse manually to handle two NamedIndividual children in ObjectPropertyAssertion
	onto := NewOntology()

	// Maps for intermediate data
	// individual -> class (string, without #)
	individualClass := make(map[string]string)
	// StopsLink -> {beginStop, endStop}
	type slinkEdge struct{ begin, end string }
	stopLinks := make(map[string]slinkEdge)
	// route -> transportID
	routeTransport := make(map[string]string)
	// route -> []stopsLinkID
	routeStopLinks := make(map[string][]string)
	// building -> nearStopID
	buildingNearStop := make(map[string]string)
	// individual -> data properties
	dataProps := make(map[string]map[string]string)

	// --- First pass: class assertions ---
	dec := xml.NewDecoder(strings.NewReader(string(data)))
	for {
		tok, err := dec.Token()
		if err == io.EOF {
			break
		}
		if err != nil {
			return nil, err
		}

		se, ok := tok.(xml.StartElement)
		if !ok {
			continue
		}

		switch se.Name.Local {
		case "ClassAssertion":
			ca := owlClassAssertion{}
			if err := dec.DecodeElement(&ca, &se); err != nil {
				continue
			}
			cls := trimIRI(ca.Class.IRI)
			ind := trimIRI(ca.NamedIndividual.IRI)
			individualClass[ind] = cls

		case "ObjectPropertyAssertion":
			// manually read children to get both NamedIndividual elements
			prop, subj, obj := parseObjPropAssertion(dec, &se)
			switch prop {
			case "beginStop":
				e := stopLinks[subj]
				e.begin = obj
				stopLinks[subj] = e
			case "endStop":
				e := stopLinks[subj]
				e.end = obj
				stopLinks[subj] = e
			case "routeTransport":
				routeTransport[subj] = obj
			case "stopLink":
				routeStopLinks[subj] = append(routeStopLinks[subj], obj)
			case "standsNear":
				buildingNearStop[subj] = obj
			}

		case "DataPropertyAssertion":
			dpa := owlDataPropAssertion{}
			if err := dec.DecodeElement(&dpa, &se); err != nil {
				continue
			}
			prop := trimIRI(dpa.DataProperty.IRI)
			ind := trimIRI(dpa.NamedIndividual.IRI)
			if dataProps[ind] == nil {
				dataProps[ind] = make(map[string]string)
			}
			dataProps[ind][prop] = dpa.Literal
		}
	}

	// --- Build Stops ---
	for id, cls := range individualClass {
		if cls != "Stop" {
			continue
		}
		name := dataProps[id]["stopName"]
		if name == "" {
			name = humanizeName(id)
		}
		onto.Stops[id] = &Stop{
			ID:   id,
			Name: name,
		}
	}

	// --- Build Transports ---
	for id, cls := range individualClass {
		var ttype TransportType
		switch cls {
		case "Bus":
			ttype = TransportBus
		case "Tram":
			ttype = TransportTram
		case "Trolleybus":
			ttype = TransportTrolleybus
		case "Metro":
			ttype = TransportMetro
		default:
			continue
		}
		num := dataProps[id]["transportNumber"]
		onto.Transports[id] = &Transport{
			ID:     id,
			Number: num,
			Type:   ttype,
		}
	}

	// --- Build Buildings ---
	buildingClasses := map[string]BuildingType{
		"School":     BuildingSchool,
		"Hospital":   BuildingHospital,
		"Polyclinic": BuildingPolyclinic,
		"Store":      BuildingStore,
		"University": BuildingUniversity,
	}
	for id, cls := range individualClass {
		btype, ok := buildingClasses[cls]
		if !ok {
			continue
		}
		name := dataProps[id]["buildingName"]
		if name == "" {
			name = humanizeName(id)
		}
		onto.Buildings[id] = &CityBuilding{
			ID:   id,
			Name: name,
			Type: btype,
		}
		// attach near-stop info so geocoder can use it later
		_ = buildingNearStop[id]
	}

	// Save near-stop mapping on buildings (reuse field NearStopID via pointer trick)
	// We'll store it as a temporary field via a side map returned in extended parse
	onto.buildingNearStop = buildingNearStop

	// --- Build Routes ---
	for id, cls := range individualClass {
		if cls != "Route" {
			continue
		}
		transportID := routeTransport[id]
		transport, ok := onto.Transports[transportID]
		if !ok {
			transport = &Transport{ID: transportID}
		}

		// Collect unique stops from stopLinks
		slinkIDs := routeStopLinks[id]
		stopSet := make(map[string]bool)
		var stops []Stop
		for _, slID := range slinkIDs {
			edge, ok := stopLinks[slID]
			if !ok {
				continue
			}
			for _, sid := range []string{edge.begin, edge.end} {
				if sid == "" || stopSet[sid] {
					continue
				}
				stopSet[sid] = true
				if s, ok := onto.Stops[sid]; ok {
					stops = append(stops, *s)
				}
			}
		}

		onto.Routes[id] = &Route{
			ID:        id,
			Transport: *transport,
			Stops:     stops,
		}
	}

	return onto, nil
}

// parseObjPropAssertion manually reads the ObjectPropertyAssertion element
// and returns (property, subject, object) trimmed IRIs
func parseObjPropAssertion(dec *xml.Decoder, start *xml.StartElement) (prop, subj, obj string) {
	var individuals []string
	for {
		tok, err := dec.Token()
		if err != nil {
			break
		}
		switch t := tok.(type) {
		case xml.StartElement:
			var iri string
			for _, attr := range t.Attr {
				if attr.Name.Local == "IRI" {
					iri = trimIRI(attr.Value)
				}
			}
			switch t.Name.Local {
			case "ObjectProperty":
				prop = iri
			case "NamedIndividual":
				individuals = append(individuals, iri)
			}
			dec.Skip()
		case xml.EndElement:
			if t.Name.Local == "ObjectPropertyAssertion" {
				goto done
			}
		}
	}
done:
	if len(individuals) >= 2 {
		subj = individuals[0]
		obj = individuals[1]
	}
	return
}

// trimIRI removes leading # from OWL local IRIs
func trimIRI(iri string) string {
	return strings.TrimPrefix(iri, "#")
}

// humanizeName converts CamelCase/underscore IDs to readable names
func humanizeName(id string) string {
	s := strings.ReplaceAll(id, "_", " ")
	return s
}
