package handlers

import (
	"math"
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"transport-ontology/internal/ontology"
)

type Handler struct {
	onto *ontology.Ontology
}

func New(onto *ontology.Ontology) *Handler {
	return &Handler{onto: onto}
}

// GET /api/stops
func (h *Handler) GetStops(c *gin.Context) {
	stops := make([]*ontology.Stop, 0, len(h.onto.Stops))
	for _, s := range h.onto.Stops {
		stops = append(stops, s)
	}
	c.JSON(http.StatusOK, stops)
}

// GET /api/stops/:id
func (h *Handler) GetStop(c *gin.Context) {
	id := c.Param("id")
	s, ok := h.onto.Stops[id]
	if !ok {
		c.JSON(http.StatusNotFound, gin.H{"error": "stop not found"})
		return
	}

	// Collect routes that pass through this stop
	var routes []routeInfo
	for _, r := range h.onto.Routes {
		for _, stop := range r.Stops {
			if stop.ID == id {
				routes = append(routes, routeInfo{
					ID:            r.ID,
					TransportType: string(r.Transport.Type),
					Number:        r.Transport.Number,
				})
				break
			}
		}
	}

	// Collect buildings near this stop
	var buildings []buildingInfo
	for bid, b := range h.onto.Buildings {
		if h.onto.BuildingNearStop(bid) == id {
			buildings = append(buildings, buildingInfo{
				ID:   b.ID,
				Name: b.Name,
				Type: string(b.Type),
				Lat:  b.Lat,
				Lon:  b.Lon,
			})
		}
	}

	c.JSON(http.StatusOK, gin.H{
		"stop":      s,
		"routes":    routes,
		"buildings": buildings,
	})
}

// GET /api/routes
func (h *Handler) GetRoutes(c *gin.Context) {
	// optional filter: ?type=Bus
	typeFilter := c.Query("type")

	routes := make([]routeResponse, 0)
	for _, r := range h.onto.Routes {
		if typeFilter != "" && string(r.Transport.Type) != typeFilter {
			continue
		}
		// Enrich stops with coordinates from ontology
		var stops []ontology.Stop
		for _, s := range r.Stops {
			if full, ok := h.onto.Stops[s.ID]; ok {
				stops = append(stops, *full)
			} else {
				stops = append(stops, s)
			}
		}
		routes = append(routes, routeResponse{
			ID:        r.ID,
			Transport: r.Transport,
			Stops:     stops,
		})
	}
	c.JSON(http.StatusOK, routes)
}

// GET /api/routes/:id
func (h *Handler) GetRoute(c *gin.Context) {
	id := c.Param("id")
	r, ok := h.onto.Routes[id]
	if !ok {
		c.JSON(http.StatusNotFound, gin.H{"error": "route not found"})
		return
	}

	var stops []ontology.Stop
	for _, s := range r.Stops {
		if full, ok := h.onto.Stops[s.ID]; ok {
			stops = append(stops, *full)
		} else {
			stops = append(stops, s)
		}
	}

	c.JSON(http.StatusOK, routeResponse{
		ID:        r.ID,
		Transport: r.Transport,
		Stops:     stops,
	})
}

// GET /api/buildings
func (h *Handler) GetBuildings(c *gin.Context) {
	buildings := make([]*ontology.CityBuilding, 0, len(h.onto.Buildings))
	for _, b := range h.onto.Buildings {
		buildings = append(buildings, b)
	}
	c.JSON(http.StatusOK, buildings)
}

// GET /api/buildings/near?lat=XX&lon=YY&radius=500
func (h *Handler) GetBuildingsNear(c *gin.Context) {
	lat, err1 := strconv.ParseFloat(c.Query("lat"), 64)
	lon, err2 := strconv.ParseFloat(c.Query("lon"), 64)
	radiusStr := c.DefaultQuery("radius", "500")
	radius, err3 := strconv.ParseFloat(radiusStr, 64)

	if err1 != nil || err2 != nil || err3 != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid lat/lon/radius"})
		return
	}

	var result []buildingInfo
	for _, b := range h.onto.Buildings {
		if b.Lat == 0 && b.Lon == 0 {
			continue
		}
		dist := haversine(lat, lon, b.Lat, b.Lon)
		if dist <= radius {
			result = append(result, buildingInfo{
				ID:       b.ID,
				Name:     b.Name,
				Type:     string(b.Type),
				Lat:      b.Lat,
				Lon:      b.Lon,
				Distance: dist,
			})
		}
	}
	c.JSON(http.StatusOK, result)
}

// GET /api/search/stops?q=Попова
func (h *Handler) SearchStops(c *gin.Context) {
	q := c.Query("q")
	if q == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "q is required"})
		return
	}
	q = toLower(q)

	var result []*ontology.Stop
	for _, s := range h.onto.Stops {
		if containsCI(s.Name, q) || containsCI(s.ID, q) {
			result = append(result, s)
		}
	}
	c.JSON(http.StatusOK, result)
}

// --- response types ---

type routeInfo struct {
	ID            string `json:"id"`
	TransportType string `json:"transport_type"`
	Number        string `json:"number"`
}

type buildingInfo struct {
	ID       string  `json:"id"`
	Name     string  `json:"name"`
	Type     string  `json:"type"`
	Lat      float64 `json:"lat"`
	Lon      float64 `json:"lon"`
	Distance float64 `json:"distance,omitempty"`
}

type routeResponse struct {
	ID        string             `json:"id"`
	Transport ontology.Transport `json:"transport"`
	Stops     []ontology.Stop    `json:"stops"`
}

// --- helpers ---

func haversine(lat1, lon1, lat2, lon2 float64) float64 {
	const R = 6371000 // Earth radius in metres
	phi1 := lat1 * math.Pi / 180
	phi2 := lat2 * math.Pi / 180
	dphi := (lat2 - lat1) * math.Pi / 180
	dlam := (lon2 - lon1) * math.Pi / 180
	a := math.Sin(dphi/2)*math.Sin(dphi/2) +
		math.Cos(phi1)*math.Cos(phi2)*math.Sin(dlam/2)*math.Sin(dlam/2)
	c := 2 * math.Atan2(math.Sqrt(a), math.Sqrt(1-a))
	return R * c
}

func toLower(s string) string {
	result := make([]rune, len([]rune(s)))
	for i, r := range []rune(s) {
		if r >= 'A' && r <= 'Z' {
			result[i] = r + 32
		} else {
			result[i] = r
		}
	}
	return string(result)
}

func containsCI(s, substr string) bool {
	return len(s) >= len(substr) &&
		(s == substr ||
			len(s) > 0 && len(substr) > 0 &&
				indexCI([]rune(s), []rune(substr)) >= 0)
}

func indexCI(s, sub []rune) int {
	for i := 0; i <= len(s)-len(sub); i++ {
		match := true
		for j, r := range sub {
			sr := s[i+j]
			// toLower for runes
			if sr >= 'A' && sr <= 'Z' {
				sr += 32
			}
			tr := r
			if tr >= 'A' && tr <= 'Z' {
				tr += 32
			}
			if sr != tr {
				match = false
				break
			}
		}
		if match {
			return i
		}
	}
	return -1
}
