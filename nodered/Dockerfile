FROM nodered/node-red:4.1.3

# Install nodes next to Node-RED, not in /data, so the volume does not hide them.
# nodered/package.json holds the version.
COPY package.json /tmp/nodes-package.json

RUN npm install --omit=dev --no-fund --no-audit --no-update-notifier \
    "@flowfuse/node-red-dashboard@$(node -p "require('/tmp/nodes-package.json').dependencies['@flowfuse/node-red-dashboard']")"

# The flows of the repository, outside /data so the volume does not hide them either.
# Every `docker compose up --build` loads nodered/flows.json; a deploy from the editor
# lasts until the container is recreated, so export it to nodered/flows.json.
COPY --chown=node-red:root flows.json /usr/src/node-red/flows.json
ENV FLOWS=/usr/src/node-red/flows.json
