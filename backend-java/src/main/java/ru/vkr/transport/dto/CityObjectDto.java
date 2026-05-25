package ru.vkr.transport.dto;

public class CityObjectDto {
    private String id;
    private String name;
    private String category;
    private String type;
    private Double latitude;
    private Double longitude;
    private String nearStopId;

    public CityObjectDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getNearStopId() { return nearStopId; }
    public void setNearStopId(String nearStopId) { this.nearStopId = nearStopId; }
}
