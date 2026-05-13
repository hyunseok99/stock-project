# Project: Stock_Client (Financial Dashboard)

## 1. Tech Stack

- **Framework:** React 19 (v19.0.4 이상 권장 - 보안 패치 적용 버전)
- **Language:** TypeScript
- **Styling:** Tailwind CSS + MUI (Material UI) + Emotion
- **State Management:** 전역 WebSocket Manager (Singleton or Context API)
- **Data Fetching:** Axios (BaseURL: http://localhost:8090)
- **Visualization:** TradingView Lightweight Charts (Main), Recharts, Chart.js

## 2. Core Features & Architecture

- **API Handling:** 비동기 처리는 Axios 인스턴스를 사용하며, `/proxy` 설정을 통해 8090 포트의 백엔드와 통신함.
- **WebSocket:** 전역 매니저를 통해 소켓 연결을 관리하며, 여러 컴포넌트에서 동일한 스트림을 구독할 수 있는 구조를 지향함.
- **Charts:** 실시간 시계열 데이터는 `lightweight-charts`를 우선적으로 사용함.

## 3. Coding Standards

- **Component:** 함수형 컴포넌트와 Hooks (`useEffect`, `useMemo`) 위주로 작성.
- **Animation:** 복잡한 전환 효과는 `framer-motion` 사용.
- **Clean Code:** Prettier와 ESLint(v9+) 규칙 준수.

## 4. Instructions for Gemini

- 새로운 컴포넌트 생성 시 MUI와 Tailwind를 조합하여 디자인할 것.
- 차트 관련 로직 작성 시 `lightweight-charts`의 성능 최적화(Ref 활용 등)를 고려할 것.
- API 호출 코드를 짤 때는 기존의 Axios 기반 비동기 처리 패턴을 유지할 것.

## Context & Performance Rules

- **Exclude Directories:** `node_modules`, `dist`, `build`, `.git`, `.vscode` 폴더는 절대 참조하지 말 것.
- **Focus Area:** 오직 `src/` 폴더 내의 소스코드(`.js`, `.jsx`, `.ts`, `.tsx`, `.css`)만 분석 대상으로 삼을 것.
- **Dependency Reference:** 라이브러리 코드를 직접 읽지 말고, `package.json`의 버전 정보를 바탕으로 최신 공식 문서를 참고하여 코드를 작성할 것.

## External Source Mapping

- **Reference Path:** `D:\shinhanDS\sol_front\src\component\stock` (이 경로에서 `stock` 하위 파일만 선별적으로 읽을 것)
- **Constraint:** 위 경로를 참조할 때도 `node_modules`는 무시하고 순수 소스 로직만 파악할 것.

## Encoding & Comment Rules

- **No Korean Comments:** 모든 소스코드(`.ts`, `.tsx`, `.js`, `.jsx`, `.css`) 내의 주석은 반드시 **영어(English)**로 작성하거나, 불필요한 경우 제거할 것. (인코딩 깨짐 방지)
- **UTF-8 Encoding:** 모든 파일은 UTF-8 (Without BOM) 형식으로 저장할 것.
- **Clean Logic Only:** 마이그레이션 시 한글 주석 때문에 로직이 왜곡되지 않도록 논리 구조 보존에 집중할 것.
