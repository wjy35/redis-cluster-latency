# Redis Standalone 과 Clsuter 의 latency 차이는 없을까?


CRC16 Hash Slot, Lettuce의 topology cache 로 Redis Cluster의 Rebalancing이 없다면 성능 차이는 적을것이라고 예상

### Summary

latency
- average 차이 1ms 미만
- p99에서만 cluster가 2ms 더 높음

throughput
- key 분산으로 cluster가 35.71% 더 높음

### Result

**Springboot** 
<img width="991" height="285" alt="Screenshot 2025-09-11 at 4 04 42 PM" src="https://github.com/user-attachments/assets/9026746f-b852-4d11-88a3-b9ac828348dd" />

<br>

**Redis Benchmark**

<img width="634" height="364" alt="Screenshot 2025-09-11 at 5 39 47 PM" src="https://github.com/user-attachments/assets/adce30ec-c23a-4ef6-a1f2-0f78c7061b6f" />


### Environment

Redis Node
- Google Cloud
- n2
- vCPU 2개
- Memory 4GB
- ubuntu 24.04

<br>

Spring
- Spring Boot 3.5.5
- Java 21
- Lettuce Client

<br>
Boot script

```shell
sudo apt-get -y update
sudo apt-get -y upgrade 
sudo timedatectl set-timezone Asia/Seoul
sudo apt install ufw
sudo ufw status verbose
sudo echo "y" | sudo ufw enable 
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow ssh
sudo ufw allow 6379
sudo ufw allow 7000:7002
sudo ufw allow 17000:17002
sudo apt-get -y install apt-transport-https ca-certificates curl gnupg-agent software-properties-common
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add
sudo add-apt-repository -y "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"
sudo apt-get -y update && sudo apt-get -y install docker-ce docker-ce-cli containerd.io
sudo usermod -aG docker ubuntu
sudo service docker restart
sudo apt install jq
DCVERSION=$(curl --silent https://api.github.com/repos/docker/compose/releases/latest | jq .name -r)
DCDESTINATION=/usr/bin/docker-compose
sudo curl -L https://github.com/docker/compose/releases/download/${DCVERSION}/docker-compose-$(uname -s)-$(uname -m) -o $DCDESTINATION
sudo chmod 755 $DCDESTINATION
sudo docker-compose -v
```

<br>
Redis Cluster docker-compose.yml

```yml
version: "3.9"

x-redis-common: &redis-common
  image: redis:7
  restart: unless-stopped
  networks: [redisnet]
  entrypoint:
    - redis-server
    - --cluster-enabled
    - "yes"
    - --cluster-config-file
    - /data/nodes.conf
    - --cluster-node-timeout
    - "5000"
    - --appendonly
    - "no"
    - --protected-mode
    - "no"
    - --bind
    - 0.0.0.0
    - --requirepass
    - "myPassword" # my_password
    - --masterauth
    - "myPassword"  # my_password
    - --cluster-announce-ip
    - 127.0.0.1  # my_ip

services:
  redis-7000:
    <<: *redis-common
    container_name: redis-7000
    command:
      - --port
      - "7000"
      - --cluster-announce-port
      - "7000"
      - --cluster-announce-bus-port
      - "17000"
    ports:
      - "7000:7000"
      - "17000:17000"

  redis-7001:
    <<: *redis-common
    container_name: redis-7001
    command:
      - --port
      - "7001"
      - --cluster-announce-port
      - "7001"
      - --cluster-announce-bus-port
      - "17001"
    ports:
      - "7001:7001"
      - "17001:17001"

  redis-7002:
    <<: *redis-common
    container_name: redis-7002
    command:
      - --port
      - "7002"
      - --cluster-announce-port
      - "7002"
      - --cluster-announce-bus-port
      - "17002"
    ports:
      - "7002:7002"
      - "17002:17002"

networks:
  redisnet:
    driver: bridge
```

<br>
start clustering

```shell
sudo docker exec -it redis-7000 redis-cli -a my_password --cluster create my_ip:7000 my_ip:7001 my_ip:7002 --cluster-replicas 0 --cluster-yes
```

<br>
check cluster

```shell
sudo docker exec -it redis-7000 redis-cli -a my_password -p 7000 CLUSTER INFO
```

<br>
Redis Standalone

```shell
sudo docker run -d \
  --name redis-standalone \
  -p 6379:6379 \
  redis:7 \
  redis-server \
    --appendonly "no" \
    --requirepass mypassword \
    --protected-mode no \
    --bind 0.0.0.0
```

<br>
ping
<br>
<img width="427" height="143" alt="Screenshot 2025-09-11 at 2 34 45 PM" src="https://github.com/user-attachments/assets/c4a13627-ee96-4dbc-aeeb-1f5b247c10b3" />

<br>
redis bench mark

```shell
docker exec -it redis-7000 \
  redis-benchmark \
  -h my_ip -p 7000 -a my_password \
  -t get,set -n 100000 -c 50
```

```shell
docker exec -it redis-7000 \
  redis-benchmark --cluster \
  -h my_ip -p 7000 -a my_password \
  -t get,set -n 100000 -c 50
```
