package ru.vkr.transport.dto;

import java.util.List;

public class RouteDto {
    private String id;
    private String routeRef;
    private String transportType;
    private String transportId;
    private String osmId;
    private List<String> stopIds;

    public RouteDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRouteRef() { return routeRef; }
    public void setRouteRef(String routeRef) { this.routeRef = routeRef; }

    public String getTransportType() { return transportType; }
    public void setTransportType(String transportType) { this.transportType = transportType; }

    public String getTransportId() { return transportId; }
    public void setTransportId(String transportId) { this.transportId = transportId; }

    public String getOsmId() { return osmId; }
    public void setOsmId(String osmId) { this.osmId = osmId; }

    public List<String> getStopIds() { return stopIds; }
    public void setStopIds(List<String> stopIds) { this.stopIds = stopIds; }
}
