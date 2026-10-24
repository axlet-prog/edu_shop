#!/bin/sh
# Локальный кластер kind cicd-lab4 с подключённым registry localhost:5001 (ЛР4).
# Скрипт «Local Registry» из документации kind (https://kind.sigs.k8s.io/docs/user/local-registry/),
# изменено только имя кластера. Его нужно передать не только в `kind create cluster`, но и в `kind get nodes`:
# без --name команда ищет ноды кластера kind по умолчанию, и registry к нодам cicd-lab4 не подключится.
#   sh k8s/kind-with-registry.sh
#   kubectl cluster-info --context kind-cicd-lab4
# Registry подключается только при создании кластера. Пересоздать: kind delete cluster --name cicd-lab4

set -o errexit

cluster_name='cicd-lab4'

# 1. Create registry container unless it already exists
reg_name='kind-registry'
reg_port='5001'
if [ "$(docker inspect -f '{{.State.Running}}' "${reg_name}" 2>/dev/null || true)" != 'true' ]; then
  docker run \
    -d --restart=always -p "127.0.0.1:${reg_port}:5000" --network bridge --name "${reg_name}" \
    registry:3
fi

# 2. Create kind cluster with containerd registry config dir enabled
cat <<EOF | kind create cluster --name "${cluster_name}" --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
containerdConfigPatches:
- |-
  [plugins."io.containerd.grpc.v1.cri".registry]
    config_path = "/etc/containerd/certs.d"
EOF

# 3. Add the registry config to the nodes
#
# localhost внутри контейнера-ноды не равен localhost хоста, поэтому containerd на нодах получает алиас:
# образы localhost:5001/... скачиваются из контейнера kind-registry
REGISTRY_DIR="/etc/containerd/certs.d/localhost:${reg_port}"
for node in $(kind get nodes --name "${cluster_name}"); do
  docker exec "${node}" mkdir -p "${REGISTRY_DIR}"
  cat <<EOF | docker exec -i "${node}" cp /dev/stdin "${REGISTRY_DIR}/hosts.toml"
[host."http://${reg_name}:5000"]
EOF
done

# 4. Connect the registry to the cluster network if not already connected
if [ "$(docker inspect -f='{{json .NetworkSettings.Networks.kind}}' "${reg_name}")" = 'null' ]; then
  docker network connect "kind" "${reg_name}"
fi

# 5. Document the local registry
# https://github.com/kubernetes/enhancements/tree/master/keps/sig-cluster-lifecycle/generic/1755-communicating-a-local-registry
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: local-registry-hosting
  namespace: kube-public
data:
  localRegistryHosting.v1: |
    host: "localhost:${reg_port}"
    help: "https://kind.sigs.k8s.io/docs/user/local-registry/"
EOF
