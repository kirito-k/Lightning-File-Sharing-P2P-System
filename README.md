# Lightning-File-Sharing-P2P-System

## Implementation

Run docker-compose located in root directory with following command.
Replace “number” by total number of peers required in system,
```
docker-compose up -d --scale peer=”number”
```
OR to rebuild,
```
docker-compose up --build -d --scale peer=”number”
```

Determine the container IDs by following command,
```
docker ps
```
Check the “Names” section from above output and remember which new terminal will be for what program
(i.e. where to miniserver and the rest of peers are running).

Run individual containers with replacing “containerID” with container ID from above output,
```
docker exec -it “containerID” sh
```

After entering terminal of containers run following for specified containers,

Run miniserver in its specific container ONLY,
```
java -cp target/p2p-1.0-SNAPSHOT.jar edu.rit.cs.MiniServer
```

Run Peers in any of remaining containers,
```
java -cp target/p2p-1.0-SNAPSHOT.jar edu.rit.cs.KademliaPeer
```
