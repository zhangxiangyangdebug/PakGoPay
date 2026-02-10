#!/usr/bin/env bash
set -euo pipefail

# ====== 可配置参数 ======
CONTAINER_NAME="pakgopay-rabbitmq"
RABBIT_IMAGE="rabbitmq:3.8-management"
RABBIT_USER="admin"
RABBIT_PASS="admin123"
RABBIT_VHOST="/"
RABBIT_PORT_AMQP="5672"
RABBIT_PORT_HTTP="15672"

DATA_VOLUME="pakgopay_rabbitmq_data"

DELAY_EXCHANGE="task.delay.exchange"
DELAY_EXCHANGE_TYPE="direct"
COLLECTING_QUEUE="task.collecting.queue"
PAYING_QUEUE="task.paying.queue"

USER_NOTIFY_EXCHANGE="user-notify"
USER_NOTIFY_EXCHANGE_TYPE="fanout"
# ========================

echo ">>> Removing old container (keep volume)..."
docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true

echo ">>> Ensuring volume exists..."
docker volume create "${DATA_VOLUME}" >/dev/null

echo ">>> Starting RabbitMQ container..."
docker run -d \
  --name "${CONTAINER_NAME}" \
  -p "${RABBIT_PORT_AMQP}:5672" \
  -p "${RABBIT_PORT_HTTP}:15672" \
  -e RABBITMQ_DEFAULT_USER="${RABBIT_USER}" \
  -e RABBITMQ_DEFAULT_PASS="${RABBIT_PASS}" \
  -v "${DATA_VOLUME}:/var/lib/rabbitmq" \
  "${RABBIT_IMAGE}"

echo ">>> Waiting for management API..."
until curl -s "http://localhost:${RABBIT_PORT_HTTP}/api/overview" >/dev/null; do
  sleep 2
done

echo ">>> Ensuring delayed message plugin exists..."
RABBIT_VERSION=$(curl -s --connect-timeout 5 --max-time 10 -u "${RABBIT_USER}:${RABBIT_PASS}" \
  "http://localhost:${RABBIT_PORT_HTTP}/api/overview" \
  | sed -n 's/.*"rabbitmq_version":"\\([^"]*\\)".*/\\1/p' || true)
if [ -z "${RABBIT_VERSION}" ]; then
  RABBIT_VERSION=$(docker exec "${CONTAINER_NAME}" rabbitmqctl version 2>/dev/null || true)
fi
echo ">>> RabbitMQ version: ${RABBIT_VERSION:-unknown}"

if [ -z "${RABBIT_VERSION}" ]; then
  echo "Failed to detect RabbitMQ version."
  exit 1
fi

RABBIT_MAJOR=$(echo "${RABBIT_VERSION}" | cut -d. -f1)
RABBIT_MINOR=$(echo "${RABBIT_VERSION}" | cut -d. -f2)
RABBIT_MAJOR_MINOR="${RABBIT_MAJOR}.${RABBIT_MINOR}"

PLUGIN_FILE="rabbitmq_delayed_message_exchange-${RABBIT_VERSION}.ez"
PLUGIN_PATH="/plugins/${PLUGIN_FILE}"
PLUGIN_URL="https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/releases/download/v${RABBIT_VERSION}/${PLUGIN_FILE}"

if ! docker exec "${CONTAINER_NAME}" test -f "${PLUGIN_PATH}"; then
  echo ">>> Downloading plugin: ${PLUGIN_URL}"
  if ! curl -fL --connect-timeout 5 --max-time 20 "${PLUGIN_URL}" -o "/tmp/${PLUGIN_FILE}"; then
    echo ">>> Versioned plugin not found, trying ${RABBIT_MAJOR_MINOR}.x tag..."
    TAGS=$(curl -s --connect-timeout 5 --max-time 20 \
      -H "User-Agent: pakgopay-setup-script" \
      https://api.github.com/repos/rabbitmq/rabbitmq-delayed-message-exchange/tags \
      | sed -n 's/.*"name":[[:space:]]*"\(v[0-9.]*\)".*/\1/p' | head -n 100)
    TAG=""
    for t in ${TAGS}; do
      if echo "${t}" | grep -q "^v${RABBIT_MAJOR_MINOR}\\."; then
        TAG="${t}"
        break
      fi
    done
    if [ -z "${TAG}" ]; then
      echo "No delayed-message plugin found for RabbitMQ ${RABBIT_MAJOR_MINOR}.x."
      echo "Set RABBIT_IMAGE to a supported version (e.g. rabbitmq:3.8-management)."
      exit 1
    fi
    PLUGIN_FILE="rabbitmq_delayed_message_exchange-${TAG#v}.ez"
    PLUGIN_PATH="/plugins/${PLUGIN_FILE}"
    TAG_URL="https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/releases/download/${TAG}/${PLUGIN_FILE}"
    echo ">>> Downloading plugin: ${TAG_URL}"
    curl -fL --connect-timeout 5 --max-time 20 "${TAG_URL}" -o "/tmp/${PLUGIN_FILE}"
  fi
  docker cp "/tmp/${PLUGIN_FILE}" "${CONTAINER_NAME}:${PLUGIN_PATH}"
fi

echo ">>> Enabling delayed message plugin..."
docker exec "${CONTAINER_NAME}" rabbitmq-plugins enable rabbitmq_delayed_message_exchange

echo ">>> Restarting RabbitMQ to apply plugin..."
docker restart "${CONTAINER_NAME}"

until curl -s "http://localhost:${RABBIT_PORT_HTTP}/api/overview" >/dev/null; do
  sleep 2
done

echo ">>> Downloading rabbitmqadmin..."
curl -s -u "${RABBIT_USER}:${RABBIT_PASS}" \
  "http://localhost:${RABBIT_PORT_HTTP}/cli/rabbitmqadmin" \
  -o /tmp/rabbitmqadmin
chmod +x /tmp/rabbitmqadmin

# ---------- helper ----------
exists_exchange() {
  /tmp/rabbitmqadmin -u "${RABBIT_USER}" -p "${RABBIT_PASS}" -V "${RABBIT_VHOST}" list exchanges name \
    | awk '{print $1}' | grep -qx "${1}"
}

exists_queue() {
  /tmp/rabbitmqadmin -u "${RABBIT_USER}" -p "${RABBIT_PASS}" -V "${RABBIT_VHOST}" list queues name \
    | awk '{print $1}' | grep -qx "${1}"
}

exists_binding() {
  /tmp/rabbitmqadmin -u "${RABBIT_USER}" -p "${RABBIT_PASS}" -V "${RABBIT_VHOST}" list bindings source destination routing_key \
    | awk '{print $1,$2,$3}' | grep -qx "${1} ${2} ${3}"
}
# ---------------------------

echo ">>> Ensuring delayed exchange..."
if exists_exchange "${DELAY_EXCHANGE}"; then
  echo "Exchange exists: ${DELAY_EXCHANGE}"
else
  /tmp/rabbitmqadmin \
    -u "${RABBIT_USER}" -p "${RABBIT_PASS}" \
    -V "${RABBIT_VHOST}" \
    declare exchange name="${DELAY_EXCHANGE}" type="x-delayed-message" durable=true \
    arguments='{"x-delayed-type":"'"${DELAY_EXCHANGE_TYPE}"'"}'
  echo "Exchange created: ${DELAY_EXCHANGE}"
fi

echo ">>> Ensuring user-notify exchange..."
if exists_exchange "${USER_NOTIFY_EXCHANGE}"; then
  echo "Exchange exists: ${USER_NOTIFY_EXCHANGE}"
else
  /tmp/rabbitmqadmin \
    -u "${RABBIT_USER}" -p "${RABBIT_PASS}" \
    -V "${RABBIT_VHOST}" \
    declare exchange name="${USER_NOTIFY_EXCHANGE}" type="${USER_NOTIFY_EXCHANGE_TYPE}" durable=true
  echo "Exchange created: ${USER_NOTIFY_EXCHANGE}"
fi

echo ">>> Ensuring collecting queue..."
if exists_queue "${COLLECTING_QUEUE}"; then
  echo "Queue exists: ${COLLECTING_QUEUE}"
else
  /tmp/rabbitmqadmin \
    -u "${RABBIT_USER}" -p "${RABBIT_PASS}" \
    -V "${RABBIT_VHOST}" \
    declare queue name="${COLLECTING_QUEUE}" durable=true
  echo "Queue created: ${COLLECTING_QUEUE}"
fi

echo ">>> Ensuring paying queue..."
if exists_queue "${PAYING_QUEUE}"; then
  echo "Queue exists: ${PAYING_QUEUE}"
else
  /tmp/rabbitmqadmin \
    -u "${RABBIT_USER}" -p "${RABBIT_PASS}" \
    -V "${RABBIT_VHOST}" \
    declare queue name="${PAYING_QUEUE}" durable=true
  echo "Queue created: ${PAYING_QUEUE}"
fi

echo ">>> Ensuring collecting binding..."
if exists_binding "${DELAY_EXCHANGE}" "${COLLECTING_QUEUE}" "${COLLECTING_QUEUE}"; then
  echo "Binding exists: ${DELAY_EXCHANGE} -> ${COLLECTING_QUEUE} (${COLLECTING_QUEUE})"
else
  /tmp/rabbitmqadmin \
    -u "${RABBIT_USER}" -p "${RABBIT_PASS}" \
    -V "${RABBIT_VHOST}" \
    declare binding source="${DELAY_EXCHANGE}" destination="${COLLECTING_QUEUE}" routing_key="${COLLECTING_QUEUE}"
  echo "Binding created: ${DELAY_EXCHANGE} -> ${COLLECTING_QUEUE} (${COLLECTING_QUEUE})"
fi

echo ">>> Ensuring paying binding..."
if exists_binding "${DELAY_EXCHANGE}" "${PAYING_QUEUE}" "${PAYING_QUEUE}"; then
  echo "Binding exists: ${DELAY_EXCHANGE} -> ${PAYING_QUEUE} (${PAYING_QUEUE})"
else
  /tmp/rabbitmqadmin \
    -u "${RABBIT_USER}" -p "${RABBIT_PASS}" \
    -V "${RABBIT_VHOST}" \
    declare binding source="${DELAY_EXCHANGE}" destination="${PAYING_QUEUE}" routing_key="${PAYING_QUEUE}"
  echo "Binding created: ${DELAY_EXCHANGE} -> ${PAYING_QUEUE} (${PAYING_QUEUE})"
fi

echo ">>> Done."
echo "RabbitMQ UI: http://localhost:${RABBIT_PORT_HTTP}"
echo "User: ${RABBIT_USER}  Pass: ${RABBIT_PASS}"
