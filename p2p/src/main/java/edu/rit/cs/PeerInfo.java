package edu.rit.cs;

import java.io.Serializable;

/*
 * Custom Peer class to hold necessary file information
 */
public class PeerInfo implements Serializable {
    private int id;
    private String url;
    private String binaryForm;

    public PeerInfo(int id, String ipAddress, String port){
        this.id = id;
        this.url = ipAddress + ":" + port;

//		Generating binary representation of the ID
        String binary = "";
        while(id > 0)
        {
            int y = id % 2;
            binary = y + binary;
            id = id / 2;
        }
        while (binary.length() < 4) binary = "0" + binary;
        this.binaryForm = binary;
    }

//    Class variable getter methods
    public String getUrl() { return this.url; }
    public int getId(){ return this.id; }
    public String getBinaryForm(){ return this.binaryForm; }
}