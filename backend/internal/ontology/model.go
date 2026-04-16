package ontology

// Stop represents a transport stop
type Stop struct {
	ID       string  `json:"id"`
	Name     string  `json:"name"`
	Lat      float64 `json:"lat"`
	Lon      float64 `json:"lon"`
	Geocoded bool    `json:"geocoded"`
}

// TransportType represents the kind of transport
type TransportType string

const (
	TransportBus        TransportType = "Bus"
	TransportTram       TransportType = "Tram"
	TransportTrolleybus TransportType = "Trolleybus"
	TransportMetro      TransportType = "Metro"
)

// Transport is a vehicle (Bus_1, Tram_40, etc.)
type Transport struct {
	ID     string        `json:"id"`
	Number string        `json:"number"`
	Type   TransportType `json:"type"`
}

// Route links a transport to an ordered sequence of stops via StopsLinks
type Route struct {
	ID        string    `json:"id"`
	Transport Transport `json:"transport"`
	Stops     []Stop    `json:"stops"`
}

// BuildingType represents city infrastructure objects
type BuildingType string

const (
	BuildingSchool     BuildingType = "School"
	BuildingHospital   BuildingType = "Hospital"
	BuildingPolyclinic BuildingType = "Polyclinic"
	BuildingStore      BuildingType = "Store"
	BuildingUniversity BuildingType = "University"
)

// CityBuilding is a named urban infrastructure object
type CityBuilding struct {
	ID   string       `json:"id"`
	Name string       `json:"name"`
	Type BuildingType `json:"type"`
	Lat  float64      `json:"lat"`
	Lon  float64      `json:"lon"`
}

// Ontology holds all parsed data
type Ontology struct {
	Stops      map[string]*Stop
	Routes     map[string]*Route
	Buildings  map[string]*CityBuilding
	Transports map[string]*Transport

	// internal: building ID -> nearest stop ID (used by geocoder)
	buildingNearStop map[string]string
}

// BuildingNearStop returns the nearest stop ID for a building
func (o *Ontology) BuildingNearStop(buildingID string) string {
	return o.buildingNearStop[buildingID]
}

func NewOntology() *Ontology {
	return &Ontology{
		Stops:      make(map[string]*Stop),
		Routes:     make(map[string]*Route),
		Buildings:  make(map[string]*CityBuilding),
		Transports: make(map[string]*Transport),
	}
}
