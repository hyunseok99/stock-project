#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# stock-project 변경 감지 기반 자동 빌드 & k3s 배포 스크립트
#
# - back/                   -> Gradle bootJar + 로컬 Docker 빌드 -> k3s ctr import -> deployment/stock-backend-server
# - stock_front/            -> 로컬 Docker 빌드 -> k3s ctr import -> deployment/stock-front-server
# - kafka/consumer/         -> 로컬 Docker 빌드 -> k3s ctr import -> deployment/stock-consumer
# - kafka/consumer_private/ -> buildx 멀티아키(amd64,arm64) Docker Hub 푸시 -> deployment/order-process + cronjob/stock-settlement-cronjob
# - kafka/consumer_balance/ -> buildx 멀티아키(amd64,arm64) Docker Hub 푸시 -> deployment/balance-process
#
# 사용 예시:
#   ./scripts/deploy.sh                          # 직전 배포 커밋 이후 변경된 서비스만 자동 배포
#   ./scripts/deploy.sh --dry-run                # 실행 없이 대상 서비스와 명령만 미리보기
#   ./scripts/deploy.sh --since 6c93e52          # 특정 커밋 이후 변경된 서비스만 배포
#   ./scripts/deploy.sh --service back --service front   # 특정 서비스 수동 지정 배포
#   ./scripts/deploy.sh --service order-process --tag v3.3
# =============================================================================

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

NAMESPACE="${K8S_NAMESPACE:-default}"
DOCKERHUB_REPO="${DOCKERHUB_REPO:-}"
MULTIARCH_PLATFORMS="${MULTIARCH_PLATFORMS:-linux/amd64,linux/arm64}"
ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-180s}"
STATE_FILE="$REPO_ROOT/.git/last-deployed-sha"

DRY_RUN="false"
NO_RECORD="false"
SINCE_REF=""
CUSTOM_TAG=""
MANUAL_SERVICES=()

usage() {
  cat <<'EOF'
Usage: ./scripts/deploy.sh [OPTIONS]

Options:
  --dry-run              실제 빌드/배포 없이 실행될 명령만 출력
  --since <git-ref>      지정한 커밋/브랜치 이후 변경된 파일 기준으로 서비스 감지
  --service <name>       배포할 서비스 수동 지정 (반복 가능)
                         가능한 값: back, front, order-process, balance-process, stock-consumer, all
  --tag <tag>            이미지 태그 직접 지정 (기본값: sha-<git short sha>)
  --no-record            배포 후 .git/last-deployed-sha 기록 생략
  -h, --help             도움말 출력
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)   DRY_RUN="true"; shift ;;
    --no-record) NO_RECORD="true"; shift ;;
    --since)     SINCE_REF="$2"; shift 2 ;;
    --service)   MANUAL_SERVICES+=("$2"); shift 2 ;;
    --tag)       CUSTOM_TAG="$2"; shift 2 ;;
    -h|--help)   usage; exit 0 ;;
    *) echo "❌ 알 수 없는 옵션: $1" >&2; usage; exit 1 ;;
  esac
done

HEAD_SHA="$(git rev-parse HEAD)"
SHORT_SHA="$(git rev-parse --short HEAD)"
TAG="${CUSTOM_TAG:-sha-${SHORT_SHA}}"

run_cmd() {
  if [[ "$DRY_RUN" == "true" ]]; then
    echo "  [DRY-RUN] $*"
  else
    echo "  ▶ $*"
    eval "$@"
  fi
}

# -----------------------------------------------------------------------------
# 1. 배포 대상 서비스 결정
# -----------------------------------------------------------------------------
TARGETS=()
add_target() {
  local svc="$1"
  for existing in "${TARGETS[@]+"${TARGETS[@]}"}"; do
    [[ "$existing" == "$svc" ]] && return 0
  done
  TARGETS+=("$svc")
}

if [[ "${#MANUAL_SERVICES[@]}" -gt 0 ]]; then
  for s in "${MANUAL_SERVICES[@]}"; do
    case "$s" in
      back|front|order-process|balance-process|stock-consumer) add_target "$s" ;;
      all)
        add_target "back"
        add_target "front"
        add_target "order-process"
        add_target "balance-process"
        add_target "stock-consumer"
        ;;
      *) echo "❌ 지원하지 않는 서비스명: $s" >&2; exit 1 ;;
    esac
  done
else
  BASE_REF="$SINCE_REF"
  if [[ -z "$BASE_REF" && -f "$STATE_FILE" ]]; then
    BASE_REF="$(cat "$STATE_FILE")"
  fi

  if [[ -z "$BASE_REF" ]]; then
    echo "ℹ️ 최초 실행입니다 (.git/last-deployed-sha 없음). 직전 커밋(HEAD~1) 기준으로 변경 사항을 확인합니다."
    echo "   (특정 기준 커밋을 원하면 --since <sha>, 직접 지정은 --service <name> 을 사용하세요.)"
    BASE_REF="HEAD~1"
  fi

  echo "🔍 변경 파일 감지: ${BASE_REF}..HEAD"
  CHANGED_FILES="$(git diff --name-only "$BASE_REF" HEAD)"

  if [[ -z "$CHANGED_FILES" ]]; then
    echo "✅ 변경된 파일이 없어 배포를 건너뜁니다."
    exit 0
  fi

  while IFS= read -r file; do
    case "$file" in
      back/*)                   add_target "back" ;;
      stock_front/*)            add_target "front" ;;
      kafka/consumer_private/*) add_target "order-process" ;;
      kafka/consumer_balance/*) add_target "balance-process" ;;
      kafka/consumer/*)         add_target "stock-consumer" ;;
    esac
  done <<< "$CHANGED_FILES"
fi

if [[ "${#TARGETS[@]}" -eq 0 ]]; then
  echo "✅ 배포 대상 서비스(back, front, order-process, balance-process, stock-consumer)에 변경 사항이 없습니다."
  exit 0
fi

echo "🚀 배포 대상 서비스: ${TARGETS[*]}"
echo "🏷️  적용 태그: ${TAG} (namespace: ${NAMESPACE}, dry-run: ${DRY_RUN})"
echo ""

# -----------------------------------------------------------------------------
# 2. 서비스별 빌드 및 k3s 반영 함수
# -----------------------------------------------------------------------------
import_to_k3s() {
  local image="$1"
  run_cmd "docker save '${image}' | sudo k3s ctr -n k8s.io images import -"
}

deploy_back() {
  local image="stock-backend-maria:${TAG}"
  echo "=== [back] Spring Boot 백엔드 빌드 & k3s 배포 (${image}) ==="

  local tmp_jar=""
  cleanup_back_jar() {
    if [[ -n "$tmp_jar" && -f "$tmp_jar" ]]; then
      rm -f "$tmp_jar"
    fi
  }
  trap cleanup_back_jar RETURN

  run_cmd "(cd '$REPO_ROOT/back' && ./gradlew bootJar -x test --no-daemon)"

  if [[ "$DRY_RUN" == "true" ]]; then
    echo "  [DRY-RUN] copy bootJar from back/build/libs/*.jar (excluding *-plain.jar) -> back/"
  else
    local built_jar
    built_jar="$(find "$REPO_ROOT/back/build/libs" -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | head -n 1)"
    if [[ -z "$built_jar" ]]; then
      echo "❌ bootJar 산출물을 찾을 수 없습니다: back/build/libs" >&2
      return 1
    fi
    tmp_jar="$REPO_ROOT/back/$(basename "$built_jar")"
    cp "$built_jar" "$tmp_jar"
  fi

  run_cmd "docker build -t '${image}' '$REPO_ROOT/back'"
  import_to_k3s "$image"
  run_cmd "kubectl -n '${NAMESPACE}' set image deployment/stock-backend-server stock-backend-server='${image}'"
  run_cmd "kubectl -n '${NAMESPACE}' rollout status deployment/stock-backend-server --timeout='${ROLLOUT_TIMEOUT}'"
  echo "✅ [back] 배포 완료"
  echo ""
}

deploy_front() {
  local image="stock-front-server:${TAG}"
  echo "=== [front] React/Vite 프론트엔드 빌드 & k3s 배포 (${image}) ==="
  run_cmd "docker build -t '${image}' '$REPO_ROOT/stock_front'"
  import_to_k3s "$image"
  run_cmd "kubectl -n '${NAMESPACE}' set image deployment/stock-front-server stock-front-server='${image}'"
  run_cmd "kubectl -n '${NAMESPACE}' rollout status deployment/stock-front-server --timeout='${ROLLOUT_TIMEOUT}'"
  echo "✅ [front] 배포 완료"
  echo ""
}

require_dockerhub_repo() {
  if [[ -z "$DOCKERHUB_REPO" ]]; then
    echo "❌ DOCKERHUB_REPO 환경변수가 필요합니다. (예: export DOCKERHUB_REPO=\"<dockerhub-user>/<repo>\")" >&2
    return 1
  fi
}

deploy_order_process() {
  require_dockerhub_repo
  local image="${DOCKERHUB_REPO}:order-process-${TAG}"
  echo "=== [order-process] 멀티아키텍처 빌드 & Docker Hub 푸시 & k3s 배포 (${image}) ==="
  run_cmd "docker buildx build --platform '${MULTIARCH_PLATFORMS}' -t '${image}' --push '$REPO_ROOT/kafka/consumer_private'"
  run_cmd "kubectl -n '${NAMESPACE}' set image deployment/order-process order-process='${image}'"
  run_cmd "kubectl -n '${NAMESPACE}' set image cronjob/stock-settlement-cronjob settlement='${image}'"
  run_cmd "kubectl -n '${NAMESPACE}' rollout status deployment/order-process --timeout='${ROLLOUT_TIMEOUT}'"
  echo "✅ [order-process + stock-settlement-cronjob] 배포 완료"
  echo ""
}

deploy_balance_process() {
  require_dockerhub_repo
  local image="${DOCKERHUB_REPO}:balance-process-${TAG}"
  echo "=== [balance-process] 멀티아키텍처 빌드 & Docker Hub 푸시 & k3s 배포 (${image}) ==="
  run_cmd "docker buildx build --platform '${MULTIARCH_PLATFORMS}' -t '${image}' --push '$REPO_ROOT/kafka/consumer_balance'"
  run_cmd "kubectl -n '${NAMESPACE}' set image deployment/balance-process balance-process='${image}'"
  run_cmd "kubectl -n '${NAMESPACE}' rollout status deployment/balance-process --timeout='${ROLLOUT_TIMEOUT}'"
  echo "✅ [balance-process] 배포 완료"
  echo ""
}

deploy_stock_consumer() {
  local image="ubuntu-stock-consumer:${TAG}"
  echo "=== [stock-consumer] 시세 컨슈머 빌드 & k3s 배포 (${image}) ==="
  run_cmd "docker build -t '${image}' '$REPO_ROOT/kafka/consumer'"
  import_to_k3s "$image"
  run_cmd "kubectl -n '${NAMESPACE}' set image deployment/stock-consumer stock-consumer='${image}'"
  run_cmd "kubectl -n '${NAMESPACE}' rollout status deployment/stock-consumer --timeout='${ROLLOUT_TIMEOUT}'"
  echo "✅ [stock-consumer] 배포 완료"
  echo ""
}

# -----------------------------------------------------------------------------
# 3. 순서대로 배포 실행 (백엔드 -> 컨슈머 -> 프론트 순)
# -----------------------------------------------------------------------------
for svc in back order-process balance-process stock-consumer front; do
  for target in "${TARGETS[@]}"; do
    if [[ "$svc" == "$target" ]]; then
      case "$svc" in
        back)            deploy_back ;;
        front)           deploy_front ;;
        order-process)   deploy_order_process ;;
        balance-process) deploy_balance_process ;;
        stock-consumer)  deploy_stock_consumer ;;
      esac
    fi
  done
done

if [[ "$DRY_RUN" != "true" && "$NO_RECORD" != "true" ]]; then
  printf '%s\n' "$HEAD_SHA" > "$STATE_FILE"
  echo "📝 마지막 배포 커밋 기록 완료: $HEAD_SHA (.git/last-deployed-sha)"
fi

echo "🎉 모든 대상 서비스 배포가 완료되었습니다!"
