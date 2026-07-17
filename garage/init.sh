#!/bin/sh
set -eu

admin="http://${GARAGE_HOST:-garage}:3903/v2"
auth="Authorization: Bearer ${GARAGE_ADMIN_TOKEN}"

call() {
  curl -fsS -H "$auth" -H "Content-Type: application/json" -X POST "$admin/$1" -d "$2"
}

until curl -fsS -H "$auth" "$admin/GetClusterStatus" > /tmp/status.json; do
  sleep 1
done

if grep -q '"layoutVersion": *0' /tmp/status.json; then
  node_id=$(grep -o '"id": *"[0-9a-f]\{64\}"' /tmp/status.json | head -n 1 | grep -o '[0-9a-f]\{64\}')
  call UpdateClusterLayout "{\"roles\":[{\"id\":\"$node_id\",\"zone\":\"local\",\"capacity\":2147483648,\"tags\":[]}]}" > /dev/null
  call ApplyClusterLayout '{"version":1}' > /dev/null
fi

call ImportKey "{\"accessKeyId\":\"${STORAGE_ACCESS_KEY}\",\"secretAccessKey\":\"${STORAGE_SECRET_KEY}\",\"name\":\"restaurant-service\"}" > /dev/null 2>&1 || true

bucket_id=$(curl -s -H "$auth" "$admin/GetBucketInfo?globalAlias=${STORAGE_BUCKET}" | grep -o '"id": *"[0-9a-f]\{64\}"' | head -n 1 | grep -o '[0-9a-f]\{64\}' || true)
if [ -z "$bucket_id" ]; then
  bucket_id=$(call CreateBucket "{\"globalAlias\":\"${STORAGE_BUCKET}\"}" | grep -o '"id": *"[0-9a-f]\{64\}"' | head -n 1 | grep -o '[0-9a-f]\{64\}')
fi

call AllowBucketKey "{\"bucketId\":\"$bucket_id\",\"accessKeyId\":\"${STORAGE_ACCESS_KEY}\",\"permissions\":{\"read\":true,\"write\":true,\"owner\":false}}" > /dev/null
