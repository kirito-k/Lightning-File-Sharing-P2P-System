# Lightning-File-Sharing-P2P-System
Lightning File Sharing P2P System is a peer to peer file sharing system based on Kademlia 
routing. Kademlia is an optimized routing algorithm popularly used in distributed systems.


Time complexity of File sharing and routing is O(log(N)), where N is number of nodes in the system.

## Prerequisites 
- Java
- Maven

## Implementation
- Clone this repository. 
- Run the following commands in terminal. 
- For running docker-compose if running this repository first time.
Replace “number” by total number of peers required in system,
```
docker-compose up -d --scale peer=”number”
```
OR to rebuild(if you performed any changes later, use below command instead of the above),
```
docker-compose up --build -d --scale peer=”number”
```

- Determine the container IDs by following command,
```
docker ps
```
- Check the “Names” section from above output and remember which new terminal will be for what program
(i.e. container IDs for miniserver and the rest of peers).
- Open new terminals, one for each peer and run individual containers with following command. Replace 
“containerID” with container ID from above output,
```
docker exec -it “containerID” sh
```

- Above command will open terminals of individual docker containers. 
Run the following command for specified containers,
- Firstly, run following command in miniserver container(terminal opened using miniserver ContainerID)
```
java -cp target/p2p-1.0-SNAPSHOT.jar edu.rit.cs.MiniServer
```

- Once the miniserver container is up, run following command in any Peer containers remaining,
```
java -cp target/p2p-1.0-SNAPSHOT.jar edu.rit.cs.KademliaPeer
```

Very clean and easy to understand GUI menu will show up.
Let me know if you have any issues.

Enjoy!
