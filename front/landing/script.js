// PDF.js 라이브러리 동적 로드 (CDN)
const pdfScript = document.createElement("script");
pdfScript.src =
  "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js";
document.head.appendChild(pdfScript);
pdfScript.onload = () => {
  pdfjsLib.GlobalWorkerOptions.workerSrc =
    "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js";
};

// Marked.js 라이브러리 동적 로드 (마크다운 파싱용)
const markedScript = document.createElement("script");
markedScript.src = "https://cdn.jsdelivr.net/npm/marked/marked.min.js";
document.head.appendChild(markedScript);

// 1. 프로젝트별 상태 관리 (슬라이더 인덱스 등)
const slideStates = {};

function renderProjects() {
  const cardContainer = document.getElementById("card-container");
  const detailsContainer = document.getElementById("details-container");

  if (!cardContainer || !detailsContainer) return;

  cardContainer.innerHTML = "";
  detailsContainer.innerHTML = "";

  projects.forEach((project) => {
    // PPT 슬라이드 상태 초기화
    if (
      project.media &&
      (project.media.type === "ppt" || project.media.type === "pdf")
    ) {
      slideStates[project.id] = 0;
    }

    // 함수 호출을 통해 HTML 삽입
    cardContainer.innerHTML += createCardHTML(project);
    detailsContainer.innerHTML += createDetailsHTML(project);
  });
}

/**
 * 프로젝트 카드 HTML 생성
 */
function createCardHTML(project) {
  // 배포 여부에 따른 버튼 스타일 및 문구 결정
  const deployBtn = project.isDeployed
    ? `<a href="${project.link}" class="btn" target="_blank">프로젝트 이동</a>`
    : `<a href="javascript:void(0)" class="btn btn-disabled" style="background: #6c757d; cursor: not-allowed;">준비 중인 서비스</a>`;

  // Grafana 링크가 존재할 경우 버튼 추가 생성
  const grafanaBtn = project.grafanaLink
    ? `<a href="${project.grafanaLink}" class="btn" target="_blank" style="background: #f46800;">Grafana 대시보드 이동</a>`
    : ``;

  return `
        <div class="card">
            <h2>${project.title}</h2>
            <p>${project.summary}</p>
            <div style="display: flex; flex-direction: column; gap: 10px; margin-top: 15px;">
                ${deployBtn}
                ${grafanaBtn}
                <a href="javascript:void(0)" class="btn" style="background: #17a2b8;" 
                   onclick="openDetail('${project.id}')">상세 설명 보기</a>
            </div>
        </div>
    `;
}

/**
 * 상세 설명(details) 섹션 HTML 생성
 */
function createDetailsHTML(project) {
  // 팜플렛이 있는 경우 PPT 아래에 보여줄 영역 생성
  const pamphletHTML = project.pamphlet
    ? `
      <div class="pamphlet-section" style="margin-top: 40px; border-top: 1px dashed #ddd; padding-top: 30px;">
          <h4 style="text-align:center; margin-bottom: 20px; color: #555;">📄 프로젝트 팜플렛</h4>
          <div id="pamphlet-viewport-${project.id}" style="display: flex; flex-direction: column; gap: 20px; align-items: center; background: #fdfdfd; padding: 20px; border-radius: 8px;">
              <div class="pdf-loading">팜플렛을 불러오는 중입니다...</div>
          </div>
      </div>
      `
    : "";

  // 마크다운 파일(readmeUrl)이 설정되어 있으면 마크다운 컨테이너를, 아니면 기존 caseSteps 렌더링
  const contentHTML = project.readmeUrl
    ? `<div id="md-container-${project.id}" class="markdown-body case-study-container">
         <div style="text-align:center; padding: 30px; color:#888;">마크다운 문서를 불러오는 중입니다...</div>
       </div>`
    : `<div class="case-study-container">${renderCaseStudy(project.caseSteps)}</div>`;

  return `
        <div class="project-detail-view">
            <h3 style="text-align:center; margin-bottom:20px;">🔍 ${project.description}</h3>
            <div class="details-content">
                <div class="media-container">${renderMedia(project)}</div>
                ${pamphletHTML}
                ${contentHTML}
            </div>
        </div>
    `;
}

/**
 * 미디어 타입별 렌더링 (PPT 내 이미지/영상 혼합 지원)
 */
function renderMedia(project) {
  const m = project.media;
  if (!m) return "";

  switch (m.type) {
    case "pdf":
      // PDF는 비동기로 렌더링되므로, 틀(Container)만 먼저 반환합니다.
      return `
            <div class="ppt-container">
                <div class="slide-viewport" id="viewport-${project.id}" style="min-height: 300px; display:flex; align-items:center; justify-content:center;">
                    <div class="pdf-loading">PDF 문서를 불러오는 중입니다...</div>
                </div>
                <div class="ppt-controls">
                    <button onclick="changeSlide('${project.id}', -1)"> < </button>
                    <span id="num-${project.id}" style="color: #333;">1 / -</span>
                    <button onclick="changeSlide('${project.id}', 1)"> > </button>
                </div>
            </div>`;
    case "ppt":
      // 모든 슬라이드를 미리 생성 (처음엔 전부 숨김)
      const slidesHTML = m.slides
        .map((src, index) => {
          const isVideo = src.toLowerCase().endsWith(".mp4");
          const style = index === 0 ? "display: block;" : "display: none;"; // 첫 장만 보여줌

          return isVideo
            ? `<video class="slide-item" data-index="${index}" src="${src}" autoplay loop muted controls style="${style} width:100%;"></video>`
            : `<img class="slide-item" data-index="${index}" src="${src}" style="${style} width:100%;">`;
        })
        .join("");

      return `
            <div class="ppt-container">
                <div class="slide-viewport" id="viewport-${project.id}">
                    ${slidesHTML}
                </div>
                <div class="ppt-controls">
                    <button onclick="changeSlide('${project.id}', -1)"> < </button>
                    <span id="num-${project.id}" style="color: #333;">1 / ${m.slides.length}</span>
                    <button onclick="changeSlide('${project.id}', 1)"> > </button>
                </div>
            </div>`;
    case "video":
      return `<video controls><source src="${m.src}" type="video/mp4">브라우저가 비디오를 지원하지 않습니다.</video>`;
    default:
      return `<img src="${m.src}" alt="${project.title} 메인 이미지">`;
  }
}

/**
 * 슬라이드 이동 및 태그 동적 교체 함수
 */
function changeSlide(projectId, direction) {
  // 데이터 객체가 아닌 실제 DOM 요소를 기준으로 개수 파악 (PDF 대응)
  const viewport = document.getElementById(`viewport-${projectId}`);
  const allSlides = viewport.querySelectorAll(".slide-item");
  const slidesCount = allSlides.length;

  if (slidesCount === 0) return; // 로딩 전이면 동작 안 함

  slideStates[projectId] += direction;
  if (slideStates[projectId] < 0) slideStates[projectId] = 0;
  if (slideStates[projectId] >= slidesCount)
    slideStates[projectId] = slidesCount - 1;

  // 모든 슬라이드 숨기고 현재 인덱스만 보여주기 (네트워크 요청 없음!)
  allSlides.forEach((slide, index) => {
    slide.style.display = index === slideStates[projectId] ? "block" : "none";
  });

  document.getElementById(`num-${projectId}`).innerText =
    `${slideStates[projectId] + 1} / ${slidesCount}`;
}

/**
 * 고민-해결-결과 단계별 섹션 렌더링
 */
/**
 * 고민-해결-결과 단계별 섹션 렌더링 (아키텍처 최적화)
 */
function renderCaseStudy(steps) {
  if (!steps || !Array.isArray(steps)) return "";

  return steps
    .map(
      (step) => `
        <div class="case-step">
            <h4 class="step-title">📌 ${step.title}</h4>
            <div class="step-body">
                <div class="step-text">
                    <p class="step-label">💡 고민과 문제</p>
                    <p class="step-desc">${step.issue}</p>
                    
                    <p class="step-label">🛠️ 해결 방안</p>
                    <p class="step-desc">${step.solution}</p>
                    
                    ${
                      step.result
                        ? `
                    <p class="step-label">✅ 결과 및 성과</p>
                    <p class="step-desc">${step.result}</p>`
                        : ""
                    }
                </div>
                
                ${
                  step.image
                    ? `
                <div class="step-image">
                    <img src="${step.image}" alt="${step.title}" 
                         title="클릭하여 원본 보기" 
                         onclick="window.open(this.src)">
                    <p class="image-caption">▲ 시스템 아키텍처 설계도</p>
                </div>`
                    : ""
                }
            </div>
        </div>
    `,
    )
    .join("");
}

/**
 * 상세 설명 보기 토글 및 동적 내용 주입
 */
function openDetail(id) {
  const section = document.querySelector(".description-section");
  const container = document.getElementById("details-container");

  if (!section || !container) return;

  // 현재 열려 있는 ID 확인
  const currentOpenId = container.getAttribute("data-current-id");

  // 같은 걸 다시 누르면 닫기
  if (currentOpenId === id) {
    section.classList.remove("active");
    container.innerHTML = "";
    container.setAttribute("data-current-id", "");
    return;
  }

  // 데이터 찾기
  const project = projects.find((p) => p.id === id);
  if (!project) return;

  // [중요] 내용을 갈아끼울 때 slideStates를 0으로 초기화해줘야 첫 페이지부터 나옵니다.
  slideStates[id] = 0;
  container.innerHTML = createDetailsHTML(project);
  container.setAttribute("data-current-id", id);

  // PDF 타입인 경우, 렌더링 시작
  if (project.media && project.media.type === "pdf") {
    renderPdfSlides(project.id, project.media.src);
  }

  // 팜플렛이 있는 경우, 스크롤 뷰로 렌더링 시작
  if (project.pamphlet) {
    renderPdfToScroll(project.id, project.pamphlet);
  }

  // 마크다운 렌더링 시작
  if (project.readmeUrl) {
    fetchAndRenderMarkdown(project.id, project.readmeUrl);
  }

  section.classList.add("active");

  setTimeout(() => {
    container.scrollIntoView({ behavior: "smooth", block: "start" });
  }, 100);
}

/**
 * 마크다운 파일을 fetch로 읽어와서 HTML로 변환 및 삽입
 */
async function fetchAndRenderMarkdown(projectId, mdUrl) {
  const container = document.getElementById(`md-container-${projectId}`);
  if (!container) return;

  try {
    const response = await fetch(mdUrl);
    if (!response.ok) throw new Error("네트워크 응답이 정상이 아닙니다.");
    const markdownText = await response.text();

    // marked.js를 활용해 마크다운을 HTML로 변환
    container.innerHTML = marked.parse(markdownText);
  } catch (error) {
    console.error("마크다운 로드 실패:", error);
    container.innerHTML = `<div style="color:red; text-align:center;">상세 설명을 불러오지 못했습니다.</div>`;
  }
}

/**
 * PDF 파일을 읽어서 각 페이지를 Canvas(이미지)로 변환하여 뷰포트에 삽입
 */
async function renderPdfSlides(projectId, pdfUrl) {
  if (!window.pdfjsLib) {
    console.error("PDF.js 라이브러리가 로드되지 않았습니다.");
    return;
  }

  const viewportContainer = document.getElementById(`viewport-${projectId}`);
  viewportContainer.style.height = "400px";
  viewportContainer.style.overflow = "hidden"; // 영역 밖으로 나가는 큰 페이지 방지
  viewportContainer.style.display = "flex";
  viewportContainer.style.alignItems = "center"; // 세로 중앙 정렬 (높이가 낮은 페이지 대비)
  viewportContainer.style.justifyContent = "center";
  const numDisplay = document.getElementById(`num-${projectId}`);

  try {
    const loadingTask = pdfjsLib.getDocument(pdfUrl);
    const pdf = await loadingTask.promise;

    // 로딩 메시지 제거
    viewportContainer.innerHTML = "";

    // 페이지 수 업데이트
    numDisplay.innerText = `1 / ${pdf.numPages}`;

    // 각 페이지를 순회하며 캔버스 생성
    for (let pageNum = 1; pageNum <= pdf.numPages; pageNum++) {
      const page = await pdf.getPage(pageNum);
      // scale 값을 높여 고해상도(Retina) 디스플레이에서 선명하게 보이도록 합니다.
      // 기존 1.5에서 3.0으로 두 배 늘려 렌더링 품질을 향상시킵니다.
      const scale = 3.0;
      const viewport = page.getViewport({ scale: scale });

      const canvas = document.createElement("canvas");
      canvas.className = "slide-item";
      canvas.style.maxWidth = "100%";
      canvas.style.maxHeight = "100%";
      canvas.style.objectFit = "contain"; // 비율 유지하며 컨테이너에 맞춤
      canvas.style.display = pageNum === 1 ? "block" : "none"; // 첫 장만 표시
      canvas.style.width = "100%";
      canvas.height = viewport.height;
      canvas.width = viewport.width;

      viewportContainer.appendChild(canvas);

      const context = canvas.getContext("2d");
      await page.render({ canvasContext: context, viewport: viewport }).promise;
    }
  } catch (error) {
    console.error("PDF 렌더링 오류:", error);
    viewportContainer.innerHTML = `<div style="color:red; text-align:center;">PDF를 불러오지 못했습니다.<br>${error.message}</div>`;
  }
}

/**
 * 팜플렛용 PDF 렌더링 (슬라이더 아님, 세로 스크롤 전체 보기)
 */
async function renderPdfToScroll(projectId, pdfUrl) {
  if (!window.pdfjsLib) return;

  const container = document.getElementById(`pamphlet-viewport-${projectId}`);
  if (!container) return;

  try {
    const loadingTask = pdfjsLib.getDocument(pdfUrl);
    const pdf = await loadingTask.promise;

    container.innerHTML = ""; // 로딩 문구 제거

    for (let pageNum = 1; pageNum <= pdf.numPages; pageNum++) {
      const page = await pdf.getPage(pageNum);
      // scale 값을 높여 고해상도(Retina) 디스플레이에서 선명하게 보이도록 합니다.
      // 기존 1.5에서 3.0으로 두 배 늘려 렌더링 품질을 향상시킵니다.
      const scale = 3.0;
      const viewport = page.getViewport({ scale: scale });

      const canvas = document.createElement("canvas");
      canvas.style.width = "100%";
      canvas.style.maxWidth = "800px"; // 팜플렛이 너무 커지지 않게 제한
      canvas.style.boxShadow = "0 4px 10px rgba(0,0,0,0.1)"; // 종이 느낌 그림자
      canvas.height = viewport.height;
      canvas.width = viewport.width;

      container.appendChild(canvas);

      const context = canvas.getContext("2d");
      page.render({ canvasContext: context, viewport: viewport }); // 비동기 대기 안 함(순차 렌더링 불필요)
    }
  } catch (error) {
    console.error("팜플렛 렌더링 오류:", error);
    container.innerHTML = `<div style="color:red; text-align:center;">팜플렛을 불러오지 못했습니다.</div>`;
  }
}

/**
 * 모든 프로젝트의 이미지를 미리 로드하여 끊김 방지
 */
function preloadAllImages() {
  projects.forEach((project) => {
    if (project.media && project.media.slides) {
      project.media.slides.forEach((src) => {
        const img = new Image();
        img.src = src; // 브라우저 캐시에 미리 저장됨
      });
    }
  });
}
// 초기화 실행
window.onload = () => {
  renderProjects();
  preloadAllImages();
};
