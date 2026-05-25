package ru.vkr.transport.dto;

import java.util.List;

public class StopDto {
    private String id;
    private String name;
    private Double latitude;
    private Double longitude;
    private String osmId;
    private boolean isTransferPoint;
    private List<String> routeIds;

    public StopDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getOsmId() { return osmId; }
    public void setOsmId(String osmId) { this.osmId = osmId; }

    public boolean isTransferPoint() { return isTransferPoint; }
    public void setTransferPoint(boolean transferPoint) { isTransferPoint = transferPoint; }

    public List<String> getRouteIds() { return routeIds; }
    public void setRouteIds(List<String> routeIds) { this.routeIds = routeIds; }
}
