package main

import (
	"log"
	"os"
	"time"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"

	"transport-ontology/internal/geocoder"
	"transport-ontology/internal/handlers"
	"transport-ontology/internal/ontology"
)

func main() {
	owlPath := envOr("OWL_PATH", "../TransportRoutes (1).owl")
	apiKey := envOr("YANDEX_API_KEY", "")
	port := envOr("PORT", "8081")

	log.Printf("Parsing OWL file: %s", owlPath)
	onto, err := ontology.ParseFile(owlPath)
	if err != nil {
		log.Fatalf("Failed to parse OWL: %v", err)
	}
	log.Printf("Parsed: %d stops, %d routes, %d buildings, %d transports",
		len(onto.Stops), len(onto.Routes), len(onto.Buildings), len(onto.Transports))

	if apiKey != "" {
		log.Println("Geocoding stops and buildings via Yandex Geocoder...")
		geo := geocoder.New(apiKey)
		onto.Enrich(geo)
	} else {
		log.Println("WARN: YANDEX_API_KEY not set, skipping geocoding")
	}

	r := gin.Default()

	r.Use(cors.New(cors.Config{
		AllowOrigins:     []string{"*"},
		AllowMethods:     []string{"GET", "OPTIONS"},
		AllowHeaders:     []string{"Origin", "Content-Type"},
		ExposeHeaders:    []string{"Content-Length"},
		AllowCredentials: false,
		MaxAge:           12 * time.Hour,
	}))

	h := handlers.New(onto)

	api := r.Group("/api")
	{
		api.GET("/stops", h.GetStops)
		api.GET("/stops/:id", h.GetStop)
		api.GET("/routes", h.GetRoutes)
		api.GET("/routes/:id", h.GetRoute)
		api.GET("/buildings", h.GetBuildings)
		api.GET("/buildings/near", h.GetBuildingsNear)
		api.GET("/search/stops", h.SearchStops)
	}

	log.Printf("Server starting on :%s", port)
	if err := r.Run(":" + port); err != nil {
		log.Fatalf("Server error: %v", err)
	}
}

func envOr(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
