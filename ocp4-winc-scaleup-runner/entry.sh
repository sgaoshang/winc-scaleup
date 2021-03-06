#!/bin/env bash

set -xe

# export RUNNER_WORKER_DIR=${WORKSPACE}/v4-testing-misc/v4-scaleup-next/ocp4-winc-scaleup-runner/${OCP_VERSION}
export RUNNER_WORKER_DIR=${WORKSPACE}/ocp4-winc-scaleup-runner/${OCP_VERSION}
export WINC_REPO="windows-machine-config-bootstrapper"

cd ${RUNNER_WORKER_DIR}
  rm -rf ${WINC_REPO}; git clone https://github.com/openshift/${WINC_REPO} --depth=1
  rm -rf kubeconfig; wget ${KUBECONFIG_URL} --no-check-certificate
  export KUBECONFIG="${RUNNER_WORKER_DIR}/kubeconfig"
  export CLUSTER_ADDRESS=`oc cluster-info | grep -Po '(?<=https://api.).*(?=:6443)'`

  if [ X${WINC_WORKERS} == X"" ]
  then
    echo "WINC_WORKERS is blank ..."
    exit 1
  fi

  # TODO: add multply workes in parallel when supported in wsu
  for WORKER in ${WINC_WORKERS}
  do
    echo ${WORKER}
    IFS="," read -ra WORKER_INFO <<< ${WORKER}
    export WINC_NODE_IP=${WORKER_INFO[0]}
    export WINC_NODE_USER=${WORKER_INFO[1]}
    export WINC_NODE_PASSWORD=${WORKER_INFO[2]}

    # inventory generation
    ansible-playbook generate_inventory.yaml -v
    cat inventory

    # Check windows connection
    ansible win -i inventory -m win_ping -v

    case "${OPERATION}" in
    "SCALEUP" )
      # ansible-playbook -i inventory scaleup_pre_hook.yaml -v
      ansible-playbook -i inventory ${RUNNER_WORKER_DIR}/${WINC_REPO}/tools/ansible/tasks/wsu/main.yaml -v
      ansible-playbook -i inventory scaleup_post_hook.yaml -v
    ;;
    * )
      echo "Unknown operation => ${OPERATION} <=, possible ops are 'SCALEUP'"
    ;;
    esac
  done
cd -