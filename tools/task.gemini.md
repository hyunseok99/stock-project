---
model: gemini-3-flash-preview # 모델 선택
yolo: true
---

# 프로젝트 데이터 흐름 및 파일 간 관계 분석

당신은 시니어 시스템 아키텍트입니다. 제공된 파일들의 소스 코드를 분석하여 데이터가 어떤 순서로 처리되고, 파일 간에 어떤 의존성이 있는지 설명해 주세요.

특히 다음 사항에 집중해 주세요:

1. 데이터가 어느 파일에서 시작되어 어디로 흐르는지 (Data Pipeline)
2. 실행 순서나 처리 순서가 보장되는 메커니즘
3. 파일별 핵심 역할 요약

# 프로젝트 설정 (환경 파악용)

@D:\Stock_oracle_instance\front\stock_front\package.json
@D:\Stock_oracle_instance\front\stock_front\tsconfig.json

# 핵심 비즈니스 로직 및 API 연동부 (순서 및 흐름 파악용)

@D:\Stock_oracle_instance\front\stock_front\src\*\*\*.ts
@D:\Stock_oracle_instance\front\stock_front\src\*\*\*.tsx

# 위 파일들 외에 전체 구조 참고:

!tree D:\Stock_oracle_instance\front\stock_front\src -L 2
