# 1단계: 빌드
FROM node:20-alpine AS build-stage
WORKDIR /app

# 의존성 설치 (캐시 활용)
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# .env.production의 VITE_API_BASE_URL=/ 설정이 빌드 결과물에 포함됩니다.
RUN npm run build

# 2단계: 실행 (정적 파일 서빙용 내부 Nginx)
FROM nginx:stable-alpine
# 빌드 결과물을 /stock 하위 폴더로 복사
COPY --from=build-stage /app/dist /usr/share/nginx/html/stock
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]