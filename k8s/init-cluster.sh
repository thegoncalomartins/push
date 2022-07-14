#!/bin/bash
docker-compose -f ../docker-compose.yml build push --no-cache
eval $(minikube docker-env)
minikube image load thegoncalomartins/push
kubectl apply -f cluster.yml
kubectl exec -it redis-0 -- redis-cli --cluster create --cluster-replicas 1 $(kubectl get pods -l app=redis -o jsonpath='{range.items[*]}{.status.podIP}:6379 ' | rev | cut -d ' ' -f3- | rev)
