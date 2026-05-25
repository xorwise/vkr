package ru.vkr.transport.importer;

import java.util.List;

public class OsmRoute {
    private long osmId;
    private String ref;       // route number e.g. "1", "40"
    private String routeType; // bus, tram, trolleybus, subway
    private List<OsmStop> stops;

    public OsmRoute() {}

    public long getOsmId() { return osmId; }
    public void setOsmId(long osmId) { this.osmId = osmId; }

    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }

    public String getRouteType() { return routeType; }
    public void setRouteType(String routeType) { this.routeType = routeType; }

    public List<OsmStop> getStops() { return stops; }
    public void setStops(List<OsmStop> stops) { this.stops = stops; }

    public static class OsmStop {
        private long osmId;
        private String name;
        private double lat;
        private double lon;

        public OsmStop() {}

        public long getOsmId() { return osmId; }
        public void setOsmId(long osmId) { this.osmId = osmId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public double getLat() { return lat; }
        public void setLat(double lat) { this.lat = lat; }

        public double getLon() { return lon; }
        public void setLon(double lon) { this.lon = lon; }
    }
}
