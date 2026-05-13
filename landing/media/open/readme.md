## Architecture

![Open 프로젝트 아키텍처](./media/open/open-arch/open-architecture.svg)

## 벡터화 시스템

### 💡벡터화 시스템 구현 과정에서의 고민과 문제

- 각각의 기사에 대해 벡터화를 진행할 때, 벡터의 크기가 너무 방대해지는 문제 발생
- 기존의 TF-IDF만으로 핵심 토큰을 추출해 내는 부분에서 문제점을 느꼈음
- 자주 반복되는 단어지만 핵심인 경우도 고려해야 할 필요성을 느꼈음 (ex, 날씨예보 관련 뉴스 기사에서 날씨는 핵심 용어지만 기사 내에서 이용 빈도가 너무 높아 가중치가 낮앗음)

### 🛠️ 해결 방안

- 카테고리별로 분모를 설정해서 TF-IDF 벡터화를 진행했다. 예를 들어 과학 분야의 기사에는, 정치인의 이름이 주제의 핵심일 가능성은 적다고 판단하였기 때문에
- 즉, 우리가 설정한 5개의 분야에 대해서 global dictionary를 만들고 이를 이용해서 idf를 연산
-

### ✅ 결과 및 성과

- 벡터화 결과가 성공적으로 DB에 저장되어 있는 모습: {'토큰': '값', ~} 형태
  ![hdbscan](./media/open/open-imgs/vectorized.png)

## 클러스터링 시스템

### 💡 고민과 문제

- 벡터화된 데이터와 코사인 유사도를 활용하여 비슷한 군집끼리 클러스터를 형성하는 것을 목표로 설정
- hdbscan,hierarchical clustering등 다양한 방법이 존재했고 어느것을 사용할지 직접 수행 후 선택
  - hdbscan 방식으로 클러스터링을 수행했지만 원하는 군집들이 형성되지 않았음

### 🛠️ 해결 방안

- hdbscan의 문제점은 벡터가 너무 많이 존재하기 때문에, 즉 차원이 너무 많아서 모두가 개별 클러스터로 군집이 형성되는 문제가 있었음
  - 이를 해결하기 위해 차원 감소 기법을 적용했지만 오히려 해당 뉴스 기사 데이터의 식별성이 감소하여 군집화는 되었지만 원하는 정도의 클러스터 수가 나오지 않았음
- 이후 hierarchical clustering을 코사인 유사도를 활용하여 수행했을 때 만든 테스트 셋과 완전히 일치하는 군집이 형성되었고 이에 계층적 클러스터링 방식을 채택
  - 코사인 유사도를 활용하여 hierarchical clustering 수행
    ![cosine-similar](./media/open/open-imgs/cosine-similar.png)

### ✅ 결과 및 성과

- hdbscan으로 군집이 제대로 형성되지 않은 모습
  ![hdbscan](./media/open/open-imgs/hdbscan.png)

- hierarchical clustering 수행 결과 14개로 군집화된 모습과 Silhoutte-Score 좋은 모습
  ![hierarch-predict](./media/open/open-imgs/hierarch-predict.png)
  ![silhoutte](./media/open/open-imgs/silhoutte-score.png)

### 💡 고민과 문제

- 기존에는 추천하는 시스템 생각은 없었지만 이후 과정에 추가하기로 결정되면서 클러스터가 형성되면 해당 클러스터를 대표하는 토큰들을 알고 이를 이용해 선호도 알고리즘에 활용을 해야 할 필요성이 생겼음

### 🛠️ 해결 방안

- 군집화가 수행되는 과정에서 핵심이 되었던 10개의 토픽과 해당 토픽의 지분을 저장해 사용하기로 결정

### ✅ 결과 및 성과

- 특정 클러스터 마다 핵심 토픽 10개가 저장된 모습
  ![clusted-effected](./media/open/open-imgs/cluster-effected.png)

## 백엔드 시스템

### 💡 고민과 문제

- 백엔드의 기능들 중 균형잡힌 뉴스 소비를 지향하기 위해서는 결국 사용자의 선호도를 기반으로 잘 읽지 않는 분야 혹은 반대의 의견을 갖는 기사를 보여줘야 했고 이를 위한 추천 알고리즘의 설계가 필요했다
  - 문제는 결국 알고리즘이 잘 동작하는지 평가를 하기에 추천이란 시스템은 애매한 점이 문제였다

### 🛠️ 해결 방안

- 결국 수학적 이론을 활용 할 수 밖에 없었고 우리는 가정을 통해서 정상적으로 작동 함을 사전에 정의했다.
  - 방식은 랜덤으로 클러스터를 x개 뽑아오고 그 내부에서 y개씩 데이터를 뽑아오고 선호도 비교를 통해서 정렬을 수행한다
  - 이후 우리가 설정한 임계값 아래의 선호도를 갖는 데이터가 없다면 위의 가정을 반복하고 아니라면 해당 기사를 제공한다.
  - 만약 사용자의 인내심이 무한하다면 무한한 N의 시도 후에는 우리의 조건을 만족하는 기사를 추천 받게 될 것이다.
  - 사용자의 선호도를 보상하기 위한 보상 알고리즘에 활용한 아이디어
    ![user-r-value](./media/open/open-imgs/R-value.png)
- 하지만 실제 사용자의 인내심은 유한하고 결국 실제 받는 경우에는 지연 시간이 적어야 하기 때문에 최대 반복 횟수를 설정해 현실에서는 빠른 시간안에 받도록 구현했다.

### ✅ 결과 및 성과

- 유저와 특정 클러스터간의 유사도 계산 결과
  ![user-cluster-sim](./media/open/open-imgs/user-cluster-sim.png)

- 유저의 선호도 실제 모습
  ![user-pref](./media/open/open-imgs/user-pref.png)

- 실제 유저 선호도 보상 내부 연산 모습
  ![R-backend](./media/open/open-imgs/R-backend.png)

- 균형잡힌 기사 추천을 위한 API 동작
  ![recommend-api](./media/open/open-imgs/recommend-api.png)
