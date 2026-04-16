package geocoder

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"
)

const yandexGeocodeURL = "https://geocode-maps.yandex.ru/1.x/"

type Geocoder struct {
	apiKey string
	client *http.Client
}

func New(apiKey string) *Geocoder {
	return &Geocoder{
		apiKey: apiKey,
		client: &http.Client{Timeout: 10 * time.Second},
	}
}

type GeoResult struct {
	Lat float64
	Lon float64
}

// Geocode resolves a query string (e.g. "Санкт-Петербург, Улица Профессора Попова")
// to geographic coordinates using the Yandex Geocoder API.
func (g *Geocoder) Geocode(query string) (*GeoResult, error) {
	params := url.Values{}
	params.Set("apikey", g.apiKey)
	params.Set("geocode", query)
	params.Set("format", "json")
	params.Set("results", "1")

	resp, err := g.client.Get(yandexGeocodeURL + "?" + params.Encode())
	if err != nil {
		return nil, fmt.Errorf("geocode request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("geocode HTTP %d", resp.StatusCode)
	}

	var payload struct {
		Response struct {
			GeoObjectCollection struct {
				FeatureMember []struct {
					GeoObject struct {
						Point struct {
							Pos string `json:"pos"`
						} `json:"Point"`
					} `json:"GeoObject"`
				} `json:"featureMember"`
			} `json:"GeoObjectCollection"`
		} `json:"response"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		return nil, fmt.Errorf("geocode decode: %w", err)
	}

	members := payload.Response.GeoObjectCollection.FeatureMember
	if len(members) == 0 {
		return nil, fmt.Errorf("no geocode results for %q", query)
	}

	pos := members[0].GeoObject.Point.Pos // "lon lat"
	parts := strings.Fields(pos)
	if len(parts) != 2 {
		return nil, fmt.Errorf("unexpected pos format: %q", pos)
	}

	lon, err := strconv.ParseFloat(parts[0], 64)
	if err != nil {
		return nil, err
	}
	lat, err := strconv.ParseFloat(parts[1], 64)
	if err != nil {
		return nil, err
	}

	return &GeoResult{Lat: lat, Lon: lon}, nil
}
