package ontology

import (
	"fmt"
	"log"

	"transport-ontology/internal/geocoder"
)

// Enrich geocodes all stops and buildings that lack coordinates.
// It uses "Санкт-Петербург, <name>" as the geocode query for stops.
// Buildings inherit the coordinates of their nearest stop if geocoding fails.
func (o *Ontology) Enrich(geo *geocoder.Geocoder) {
	city := "Санкт-Петербург"

	for _, stop := range o.Stops {
		if stop.Geocoded {
			continue
		}
		query := fmt.Sprintf("%s, %s", city, stop.Name)
		res, err := geo.Geocode(query)
		if err != nil {
			log.Printf("WARN geocode stop %q: %v", stop.Name, err)
			continue
		}
		stop.Lat = res.Lat
		stop.Lon = res.Lon
		stop.Geocoded = true
		log.Printf("Geocoded stop %q -> %.6f, %.6f", stop.Name, res.Lat, res.Lon)
	}

	for _, b := range o.Buildings {
		if b.Lat != 0 && b.Lon != 0 {
			continue
		}
		// Try geocoding the building by name
		query := fmt.Sprintf("%s, %s", city, b.Name)
		res, err := geo.Geocode(query)
		if err != nil {
			// Fall back to nearest stop coordinates
			nearStopID := o.BuildingNearStop(b.ID)
			if nearStopID != "" {
				if s, ok := o.Stops[nearStopID]; ok && s.Geocoded {
					b.Lat = s.Lat
					b.Lon = s.Lon
					log.Printf("Building %q used stop coords (%s)", b.Name, s.Name)
					continue
				}
			}
			log.Printf("WARN geocode building %q: %v", b.Name, err)
			continue
		}
		b.Lat = res.Lat
		b.Lon = res.Lon
		log.Printf("Geocoded building %q -> %.6f, %.6f", b.Name, res.Lat, res.Lon)
	}
}
