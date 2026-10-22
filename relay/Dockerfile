FROM node:22-bookworm-slim AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src ./src
RUN npm run build
RUN npm prune --omit=dev

FROM node:22-bookworm-slim
WORKDIR /app
ENV NODE_ENV=production HOST=0.0.0.0 PORT=8080
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/dist ./dist
COPY package.json ./
COPY public ./public
COPY migrations ./migrations
COPY certs ./certs
USER node
EXPOSE 8080
CMD ["node", "dist/server.js"]
