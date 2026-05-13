import axios from "axios";

// 배포 환경에서는 Nginx가 API 요청을 프록시하므로 상대 경로('/')를 기본값으로 사용하거나
// 환경 변수를 통해 외부 도메인을 주입받습니다.
const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/",
  timeout: 10000,
  headers: {
    "Content-Type": "application/json",
  },
});

// Add request interceptor for auth tokens
axiosInstance.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("token");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

export default axiosInstance;
