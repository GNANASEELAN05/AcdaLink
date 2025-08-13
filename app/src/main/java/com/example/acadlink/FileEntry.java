package com.example.acadlink;

public class FileEntry {
    public String name;
    public String size;
    public String url;

    public FileEntry() {}

    public FileEntry(String name, String size, String url) {
        this.name = name;
        this.size = size;
        this.url = url;
    }

    public String getName() { return name; }
    public String getSize() { return size; }
    public String getUrl() { return url; }
}
