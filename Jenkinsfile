@Library('c3')
import com.c3.Jenkins.*;

timestamps {
  timeout(time: 1, unit: 'HOURS') {
    node {
      def sha = checkout(scm).GIT_COMMIT;

      withCredentials([string(credentialsId: 'devops-c3ci', variable: 'gitAuth')]) {
        stage("Clone") {
          gitCheckout('opscenter', [name: sha], [[url: githubUrl('opscenter', gitAuth)]], true);
        }

        dir('opscenter') {
          stage("Build") {
            sh "mvn clean compile package -Dmaven.test.skip=true"
          }

          stage("Release") {
            withAWS(region: 'us-west-2', role: 'arn:aws:iam::161628461045:role/prod-c3e-iam-01') {
              sh """
                export RPM_FULL_NAME=\$(ls target/rpm/opsagent/RPMS/noarch/*.rpm | grep "opsagent")
                export RPM_FILE=\${RPM_FULL_NAME##*/}
                aws s3 cp \$RPM_FULL_NAME s3://c3.internal.development.repository/rpm-repo/rpms/\$RPM_FILE
                aws s3 cp \$RPM_FULL_NAME s3://c3--packagemanager/ops/bootstrap/\$RPM_FILE
              """
            }
          }
        }
      }
    }
  }
}

