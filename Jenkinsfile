// 자주 사용되는 필요한 변수를 전역으로 선언하는 것도 가능.changedServices
def ecrLoginHelper = "docker-credential-ecr-login" // ECR credential helper 이름


// 젠킨스 선언형 파이프라인 시작. Groovy 기반의 DSL(Domain Specific Language)을 사용함.
pipeline {
  agent any // 이 파이프라인은 어떤 에이전트(젠킨스 노드)에서도 실행 가능하도록 지정함.
            // 특정 노드에서만 실행되도록 제한하고 싶을 경우 'agent { label 'node-name' }'처럼 지정할 수 있음.

  environment {
    // 공통 환경 변수 선언부. 쉘 스크립트에서도 ${SERVICE_DIRS} 형식으로 접근 가능함.
    SERVICE_DIRS = "config-service,discovery-service,gateway-service,user-service,ordering-service,product-service"
    // 여러 개의 마이크로서비스 디렉토리 이름을 콤마(,)로 구분한 문자열.
    // 이후에 각 서비스가 수정되었는지 비교할 때 기준으로 사용함.
    ECR_URL = "390844784325.dkr.ecr.ap-northeast-2.amazonaws.com/orderservice-image"
    REGION = "ap-northeast-2"
  }

  stages {
    // 파이프라인을 단계별로 나누는 구간. 각 단계는 병렬 또는 순차적으로 실행될 수 있음.

    stage('Pull Codes from Github') {
      // GitHub에서 소스를 받아오는 단계
      steps {
        checkout scm // 젠킨스에서 설정된 SCM(Git) 정보를 기반으로 현재 파이프라인 workspace에 코드를 내려받음.
                     // Jenkinsfile이 포함된 저장소 기준으로 동작함.
      }
    }

    stage('Detect Changes') {
      // Git 커밋 비교를 통해 어떤 서비스 디렉토리에 변경이 있는지 감지하는 단계
      steps {
        script {
          // 현재 브랜치의 커밋 수를 구함. (HEAD까지 몇 개의 커밋이 존재하는지 확인)
          def commitCount = sh(script: "git rev-list --count HEAD", returnStdout: true)
                              .trim()
                              .toInteger()

          def changedServices = [] // 변경된 서비스명을 저장할 리스트
          def serviceDirs = env.SERVICE_DIRS.split(",") // 문자열을 리스트로 변환

          if (commitCount == 1) {
            // 최초 커밋인 경우 (초기 커밋 상태)
            echo "Initial commit detected. All services will be built."
            changedServices = serviceDirs // 모든 서비스에 대한 변경 처리가 필요하므로 전부 포함시킴.

          } else {
            // 이전 커밋과 현재 커밋 간의 변경 파일 목록 조회
            def changedFiles = sh(script: "git diff --name-only HEAD~1 HEAD", returnStdout: true)
                                .trim()
                                .split('\n') // 변경된 파일 목록을 줄 단위로 리스트화함

            // 어떤 파일이 변경되었는지 로그에 출력
            echo "Changed files: ${changedFiles}"

            // 각 서비스 디렉토리 기준으로 변경된 파일이 존재하는지 확인
            serviceDirs.each { service ->
              // 변경된 파일들 중에서 해당 서비스 디렉토리로 시작하는 파일이 있는지 확인
              if (changedFiles.any { it.startsWith(service + "/") }) {
                changedServices.add(service)
                // 변경된 서비스만 리스트에 추가함
              }
            }
          }

          // 변경된 서비스 리스트를 다시 문자열로 변환하여 환경 변수에 저장 (다른 stage에서 사용하기 위함)
          env.CHANGED_SERVICES = changedServices.join(",")

          if (env.CHANGED_SERVICES == "") {
            // 변경된 서비스가 없다면 빌드/배포 생략하고 성공 상태로 파이프라인 종료
            echo "No changes detected in service directories. Skipping build and deployment."
            currentBuild.result = 'SUCCESS'
          }
        }
      }
    }

    stage('Build Changed Services') {
      // 변경된 서비스가 존재할 경우에만 실행되는 빌드 스테이지
      when {
        expression { env.CHANGED_SERVICES != "" }
        // env.CHANGED_SERVICES 환경변수가 비어있지 않은 경우(true일 경우)만 해당 스테이지 실행
      }
      steps {
        script {
          def changedServices = env.CHANGED_SERVICES.split(",")
          // 문자열로 되어있는 CHANGED_SERVICES를 다시 리스트로 변환

          changedServices.each { service ->
            // 각 변경된 서비스 디렉토리로 진입하여 gradle 빌드를 수행
            sh """
              echo "Building ${service}..."
              cd ${service}
              ./gradlew clean build -x test
              // -x test 옵션은 테스트 코드를 제외하고 빌드하겠다는 의미
              // CI 도중 빠르게 결과를 얻기 위해 테스트 제외

              ls -al ./build/libs
              // 빌드 결과물(JAR, WAR 등)이 잘 생성되었는지 확인

              cd ..
              // 작업 후 원래 위치로 복귀
            """
          }
        }
      }
    }

    stage('Build Docker Image & Push to AWS ECR') {
      // 변경된 서비스가 있을 경우에만 Docker 이미지 빌드 및 AWS ECR로 푸시
      when {
        expression { env.CHANGED_SERVICES != "" }
      }
      steps {
        script {
          // jenkins 에 저장된 credentials 를 사용하여 AWS 자격증명 설정
          withAWS(region: "${REGION}", credentials: "aws-key") {
            def changedServices = env.CHANGED_SERVICES.split(",")
            changedServices.each { service ->
              sh """
              # ECR 에 이미지를 push 하기 위해 인증 정보를 대신 검증해 주는 도구 다운로드
              # /usr/local/bin/ 경로에 해당 파일을 이동

              curl -O https://amazon-ecr-credential-helper-releases.s3.us-east-2.amazonaws.com/0.4.0/linux-amd64/${ecrLoginHelper}
              chmod +x ${ecrLoginHelper}
              mv ${ecrLoginHelper} /usr/local/bin/

              # Docker 에게 push 명령을 내리면 지정된 URL 로 push 할 수 있게 설정.
              # 자동으로 로그인 도구를 쓰게 설정
              echo '{"credHelpers": {"${ECR_URL}": "ecr-login"}}' > ~/.docker/config.json

              docker build -t ${service}:latest ${service}
              docker tag ${service}:latest ${ERC_URL}/${service}:latest
              """
            }
          }


          // 이 부분은 아직 비어 있으며, 일반적으로 다음과 같은 로직이 들어감:

          // 1. AWS CLI 인증
          // 2. 각 서비스에 대해 Dockerfile 경로로 이미지 빌드
          // 3. ECR(Elastic Container Registry) 리포지토리로 이미지 푸시
          // 예시:
          /*
          def changedServices = env.CHANGED_SERVICES.split(",")
          def awsAccountId = '123456789012'
          def region = 'ap-northeast-2'

          sh "aws ecr get-login-password --region ${region} | docker login --username AWS --password-stdin ${awsAccountId}.dkr.ecr.${region}.amazonaws.com"

          changedServices.each { service ->
            def imageName = "${awsAccountId}.dkr.ecr.${region}.amazonaws.com/${service}:latest"
            sh """
              cd ${service}
              docker build -t ${imageName} .
              docker push ${imageName}
              cd ..
            """
          }
          */
        }
      }
    }
  }
}