import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// https://vite.dev/config/
export default defineConfig(() => {
  return {
    base: "/stock/", // 하위 경로 설정을 추가합니다.
    plugins: [react()],
    server: {
      proxy: {
        "/api": {
          target: "http://localhost:8090",
          changeOrigin: true,
        },
      },
    },
  };
});
