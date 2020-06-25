package edu.rit.cs;

/*
 * Custom file datastructure class to hold necessary file information
 */
public class FileDataStructure {
    private byte b[] = new byte[20002];
    private String filename;
    private int fileHash;
    private PeerInfo requestingPeer;

    public FileDataStructure(int id, String filename, byte[] content, PeerInfo requestingPeer) {
        this.fileHash = id;
        this.filename = filename;
        this.b = content;
        this.requestingPeer = requestingPeer;
    }

//    Class variable getter methods
    public byte[] getContent() { return b; }
    public String getFilename() { return filename; }
    public int getFileHash() { return fileHash; }
    public PeerInfo getRequestingPeer() { return requestingPeer; }
}