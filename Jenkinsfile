// 자주 사용되는 필요한 변수를 전역으로 선언하는 것도 가능.
// 이후 쉘 스크립트에서 사용할 ECR 인증 헬퍼 이름
def ecrLoginHelper = "docker-credential-ecr-login" // ECR credential helper 이름
def deployHost = "172.31.10.176" // 배포 인스턴스의 private 주소

// 젠킨스의 선언형 파이프라인 정의부 시작 (Groovy DSL 문법 사용)
pipeline {
  agent any // 어느 젠킨스 서버에서나 실행 가능 (특정 노드에 지정하지 않음)

  environment {
    // 각 마이크로서비스의 디렉토리 경로를 쉼표로 구분한 문자열로 선언
    // 이후 서비스 변경 여부 감지 시 사용
    SERVICE_DIRS = "config-service,discovery-service,gateway-service,user-service,ordering-service,product-service"

    // AWS ECR(Elastic Container Registry) URL (ECR로 Docker 이미지 푸시 시 사용)
    ECR_URL = "390844784325.dkr.ecr.ap-northeast-2.amazonaws.com"

    // AWS 리전 정보 (ECR 및 EC2 접근 시 필요)
    REGION = "ap-northeast-2"
  }

  stages {
    // GitHub에서 코드 받아오는 단계
    stage('Pull Codes from Github') {
      steps {
        // Jenkinsfile이 속한 Git 저장소에서 현재 브랜치 코드를 체크아웃
        checkout scm
      }
    }

    // 변경된 서비스가 있는지 감지하는 단계
    stage('Detect Changes') {
      steps {
        script {
          // 현재 브랜치의 커밋 수를 가져옴
          // 초기 커밋인지 여부 판단 용도
          def commitCount = sh(script: "git rev-list --count HEAD", returnStdout: true)
                              .trim()
                              .toInteger()

          def changedServices = [] // 변경된 서비스 목록을 저장할 리스트
          def serviceDirs = env.SERVICE_DIRS.split(",") // 환경 변수로부터 서비스 디렉토리 배열 생성

          if (commitCount == 1) {
            // 최초 커밋이라면 모든 서비스가 변경된 것으로 간주
            echo "Initial commit detected. All services will be built."
            changedServices = serviceDirs
          } else {
            // 가장 최근 커밋과 그 이전 커밋 간의 변경된 파일 목록 조회
            def changedFiles = sh(script: "git diff --name-only HEAD~1 HEAD", returnStdout: true)
                                .trim()
                                .split('\n')

            echo "Changed files: ${changedFiles}" // 변경된 파일 경로 출력

            // 변경된 파일 중 각 서비스 디렉토리와 연관 있는지를 확인
            serviceDirs.each { service ->
              // 예: 변경 파일 경로가 user-service/로 시작하면 해당 서비스가 변경된 것
              if (changedFiles.any { it.startsWith(service + "/") }) {
                changedServices.add(service)
              }
            }
          }

          // 리스트를 문자열로 변환 후 환경변수에 저장 (다음 스테이지에서 사용 가능하도록)
          // 환경 변수는 문자열만 저장 가능하기 때문에 join 사용
          env.CHANGED_SERVICES = changedServices.join(",")

          if (env.CHANGED_SERVICES == "") {
            echo "No changes detected in service directories. Skipping build and deployment."
            // 변경 사항이 없으므로 파이프라인은 성공 상태로 종료
            currentBuild.result = 'SUCCESS'
          }
        }
      }
    }

    // 변경된 서비스가 있다면 빌드 수행
    stage('Build Changed Services') {
      when {
        expression { env.CHANGED_SERVICES != "" } // 변경된 서비스가 있을 때만 실행
      }
      steps {
        script {
          def changedServices = env.CHANGED_SERVICES.split(",")
          changedServices.each { service ->
            sh """
            echo "Building ${service}..."
            cd ${service}                             # 해당 서비스 디렉토리로 이동
            ./gradlew clean build -x test             # 테스트는 제외하고 빌드 수행
            ls -al ./build/libs                       # 빌드된 JAR 파일 목록 출력
            cd ..
            """
          }
        }
      }
    }

    // 빌드된 서비스의 Docker 이미지 생성 및 AWS ECR로 push
    stage('Build Docker Image & Push to AWS ECR') {
//       when {
//         expression { env.CHANGED_SERVICES != "" } // 변경된 서비스가 있을 때만 실행
//       }
      steps {
        script {
          // Jenkins에 등록된 AWS 자격 증명을 이용해 AWS CLI 명령어 실행 가능
          withAWS(region: "${REGION}", credentials: "aws-key") {
            def changedServices = env.SERVICE_DIRS.split(",")
            changedServices.each { service ->
              sh """
              # ECR Credential Helper 다운로드 및 권한 설정
              # Docker가 자동으로 ECR에 로그인할 수 있도록 해주는 도구
              curl -O https://amazon-ecr-credential-helper-releases.s3.us-east-2.amazonaws.com/0.4.0/linux-amd64/${ecrLoginHelper}
              chmod +x ${ecrLoginHelper}
              mv ${ecrLoginHelper} /usr/local/bin/

              # Docker 인증 설정 파일 생성
              # ECR 접속 시 ecr-login 헬퍼를 사용하도록 설정
              mkdir -p ~/.docker
              echo '{"credHelpers": {"${ECR_URL}": "ecr-login"}}' > ~/.docker/config.json

              # Docker 이미지 빌드, 태깅 및 ECR에 푸시
              docker build -t ${service}:latest ${service}
              docker tag ${service}:latest ${ECR_URL}/${service}:latest
              docker push ${ECR_URL}/${service}:latest
              """
            }
          }
        }
      }
    }

    // EC2에 변경된 서비스 배포 (현재 비어 있음)
    stage('Deploy Changed Services to AWS EC2') {
//       when {
//         expression { env.CHANGED_SERVICES != "" }
//       }
      steps {
        sshagent(credentials: ["deploy-key"]) {
          sh """
          # Jenkins 에서 배포 서버로 docker-compose.yml 복사 후 전송
          scp -o StrictHostKeyChecking=no docker-compose.yml ubuntu@${deployHost}:/home/ubuntu/docker-compose.yml

          ssh -o StrictHostKeyChecking=no ubuntu@${deployHost} '
          cd /home/ubuntu && \

          # 시간이 지나 로그인 만료 시 필요한 명령
          aws ecr get-login-password --region ${REGION} | docker login --username AWS --password-stdin ${ECR_URL}

          # docker compose를 이용해서 변경된 서비스만 이미지를 pull -> 일괄 실행
          docker-compose pull ${env.CHANGED_SERVICES} && \
          docker-compose up -d ${env.CHANGED_SERVICES}'
          """
        }
      }
    }
  }
}