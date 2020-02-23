pipeline {
    agent {
        label "ansible-2.8"
    }

    environment {
        // will be available in entire pipeline
        OUTPUT_PATH = "rhel_env.json"
    }

    stages {
        stage('set job name to triggered user') {
            steps {
                script {
                    def userCause = currentBuild.rawBuild.getCause(hudson.model.Cause$UserIdCause)
                    def userId = userCause?.userId
                    if (userId) {
                        currentBuild.rawBuild.setDisplayName(userId + "-" + env.SCALEUP_BUILD_NUMBER)
                    }
                }
            }
        }

        stage('Clone the repo') {
            steps {
                checkout changelog: false,
                    poll: false,
                    scm: [
                        $class: 'GitSCM', 
                        branches: [[name: "master"]],
                        doGenerateSubmoduleConfigurations: false,
                        extensions: [
                            [$class: 'CloneOption', noTags: true, reference: '', shallow: true],
                            [$class: 'PruneStaleBranch'],
                            [$class: 'CleanCheckout'],
                            [$class: 'IgnoreNotifyCommit'],
                    ],
                    submoduleCfg: [],
                    userRemoteConfigs: [[
                      credentialsId: 'c9fb86e4-bd29-425f-a834-16ef21009d84',
                      name: 'origin',
                      refspec: "+refs/heads/master:refs/remotes/origin/master",
                      url: 'ssh://openshift-jenkins@code.engineering.redhat.com:22/openshift-misc'
                      ]]
                    ]
            }
        }

        stage('Fetch artifacts from upstream job') {
            environment {
                BUILD_NUMBER = "${env.SCALEUP_BUILD_NUMBER}"
            }
            steps {
                copyArtifacts fingerprintArtifacts: true,
                              flatten: true,
                              projectName: 'ocp4-rhel-scaleup',
                              selector: specific(env.BUILD_NUMBER)
            }
        }

        stage('Prepare terraform runtime') {
            environment {
                TERRAFORM_VER = "0.12.13"
            }
            steps {
                ansiColor('gnome-terminal') {
                    sh """
                    if [ -f /usr/bin/terraform ]; then
                      echo "terraform already installed"
                    else
                      curl -s -o terraform.zip https://releases.hashicorp.com/terraform/${TERRAFORM_VER}/terraform_${TERRAFORM_VER}_linux_amd64.zip && 
                      unzip terraform.zip && 
                      sudo mv ./terraform /usr/bin/terraform
                    fi
                    """
                }
            }
        }

        stage('Destroy the rhel hosts') {
            environment {
              TERRAFORM_DIR = "${readFile("terraform_dir")}"
              TF_VAR_CLUSTER_INFO_PATH = "${env.WORKSPACE}"
            }
            steps {
                script {
                    ansiColor('gnome-terminal') {
                      withCredentials([
                        file(credentialsId: '87fc15e7-df49-4b7c-9ecd-c7151d303fd8', variable: 'TF_VAR_OSP_CREDS'),
                        file(credentialsId: 'b73d6ed3-99ff-4e06-b2d8-64eaaf69d1db', variable: 'TF_VAR_AWS_CREDS'),
                        file(credentialsId: 'eb22dcaa-555c-4ebe-bb39-5b25628cc6bb', variable: 'TF_VAR_GCE_CREDS'),
                        file(credentialsId: '94fd75b0-9dd4-4322-b234-5f32e0734af4', variable: 'TF_VAR_VSPHERE_CREDS')
                        ]) {
                        sh """
                        source ./cleaner_env.sh
                        terraform init ${TERRAFORM_DIR}
                        terraform destroy -auto-approve ${TERRAFORM_DIR}
                        """
                      }
                    }
                }
            }
        }
    }
}
