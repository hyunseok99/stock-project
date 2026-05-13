// !!! [FEAT] Gemini-CLI를 활용해서 프로젝트별 descriptions 관리

const projects = [
  {
    id: "stock-detail",
    title: "실시간 데이터 제공 서비스 구현",
    description: "수행한 프로젝트 PPT 자료",
    summary: "Kafka 기반 데이터 수집, 전달 파이프라인 설계 및 구현",
    link: "/stock/",
    grafanaLink: "/grafana/",
    isDeployed: true,
    media: {
      type: "pdf",
      src: "/media/stock/stock-ppts/stock_project.pdf",
    },
    readmeUrl: "/media/stock/readme.md",
  },
  {
    id: "open",
    title: "뉴스 기사 클러스터링 시스템 및 균형잡힌 추천 시스템 구현",
    description: "수행한 프로젝트 PPT 자료",
    summary:
      "데이터 전처리, 데이터 벡터화 및 클러스터링 시스템 개발, 백엔드 서비스 개발",
    link: "/open/",
    isDeployed: false,
    media: {
      type: "pdf",
      src: "/media/open/open-ppts/open_project.pdf",
    },
    pamphlet: "/media/open/open-ppts/open_pamphlet.pdf",
    readmeUrl: "/media/open/readme.md",
  },
];
