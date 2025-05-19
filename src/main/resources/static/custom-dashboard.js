// 차트 객체 저장 변수
let queryTypeChart;
let queryTimelineChart;
let queryTimeDistributionChart;

// 데이터 저장 변수
let queryData = {
  select: 0,
  insert: 0,
  update: 0,
  delete: 0,
  other: 0
};

let timelineData = {
  labels: [],
  selectData: [],
  insertData: [],
  updateData: [],
  deleteData: []
};

let queryTimeData = {
  fast: 0,    // < 10ms
  medium: 0,  // 10-100ms
  slow: 0,    // 100-500ms
  verySlow: 0 // > 500ms
};

let queryLogs = [];

// 자동 새로고침 상태
let autoRefresh = true;
let refreshInterval;
let refreshIntervalTime = 5000; // 5초마다 업데이트

// 페이지 로드 시 초기화
document.addEventListener('DOMContentLoaded', function() {
  initCharts();
  setupEventListeners();
  startAutoRefresh();

  // 초기 데이터 로드
  fetchMetricsData();
});

// 이벤트 리스너 설정
function setupEventListeners() {
  // 서비스 선택 변경 이벤트
  document.getElementById('serviceSelector').addEventListener('change', function() {
    fetchMetricsData();
  });

  // 일시정지/재개 버튼 이벤트
  document.getElementById('pauseButton').addEventListener('click', function() {
    toggleAutoRefresh();
  });

  // 새로고침 버튼 이벤트
  document.getElementById('refreshButton').addEventListener('click', function() {
    fetchMetricsData();
  });

  // 필터 변경 이벤트 리스너
  document.getElementById('queryFilter').addEventListener('change', updateQueryLogTable);

  // 로그 초기화 이벤트 리스너
  document.getElementById('clearLogs').addEventListener('click', function() {
    queryLogs = [];
    updateQueryLogTable();
  });
}

// 자동 새로고침 시작
function startAutoRefresh() {
  if (refreshInterval) {
    clearInterval(refreshInterval);
  }

  refreshInterval = setInterval(function() {
    if (autoRefresh) {
      fetchMetricsData();
    }
  }, refreshIntervalTime);
}

// 자동 새로고침 토글
function toggleAutoRefresh() {
  autoRefresh = !autoRefresh;
  const pauseButton = document.getElementById('pauseButton');

  if (autoRefresh) {
    pauseButton.innerHTML = '<i class="bi bi-pause-circle"></i> 일시정지';
    pauseButton.classList.remove('btn-success');
    pauseButton.classList.add('btn-outline-light');
  } else {
    pauseButton.innerHTML = '<i class="bi bi-play-circle"></i> 재개';
    pauseButton.classList.remove('btn-outline-light');
    pauseButton.classList.add('btn-success');
  }
}

// 차트 초기화
function initCharts() {
  // 쿼리 종류별 차트 (도넛 차트)
  const queryTypeCtx = document.getElementById('queryTypeChart').getContext('2d');
  queryTypeChart = new Chart(queryTypeCtx, {
    type: 'doughnut',
    data: {
      labels: ['SELECT', 'INSERT', 'UPDATE', 'DELETE', '기타'],
      datasets: [{
        data: [0, 0, 0, 0, 0],
        backgroundColor: [
          'rgba(54, 162, 235, 0.7)',
          'rgba(75, 192, 192, 0.7)',
          'rgba(255, 206, 86, 0.7)',
          'rgba(255, 99, 132, 0.7)',
          'rgba(153, 102, 255, 0.7)'
        ],
        borderWidth: 1
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          position: 'right'
        }
      }
    }
  });

  // 시간별 쿼리 추이 차트 (라인 차트)
  const queryTimelineCtx = document.getElementById('queryTimelineChart').getContext('2d');
  queryTimelineChart = new Chart(queryTimelineCtx, {
    type: 'line',
    data: {
      labels: [],
      datasets: [
        {
          label: 'SELECT',
          data: [],
          borderColor: 'rgba(54, 162, 235, 1)',
          backgroundColor: 'rgba(54, 162, 235, 0.1)',
          tension: 0.4
        },
        {
          label: 'INSERT',
          data: [],
          borderColor: 'rgba(75, 192, 192, 1)',
          backgroundColor: 'rgba(75, 192, 192, 0.1)',
          tension: 0.4
        },
        {
          label: 'UPDATE',
          data: [],
          borderColor: 'rgba(255, 206, 86, 1)',
          backgroundColor: 'rgba(255, 206, 86, 0.1)',
          tension: 0.4
        },
        {
          label: 'DELETE',
          data: [],
          borderColor: 'rgba(255, 99, 132, 1)',
          backgroundColor: 'rgba(255, 99, 132, 0.1)',
          tension: 0.4
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      scales: {
        y: {
          beginAtZero: true
        }
      }
    }
  });

  // 쿼리 실행 시간 분포 차트 (바 차트)
  const queryTimeDistributionCtx = document.getElementById('queryTimeDistributionChart').getContext('2d');
  queryTimeDistributionChart = new Chart(queryTimeDistributionCtx, {
    type: 'bar',
    data: {
      labels: ['빠름 (<10ms)', '보통 (10-100ms)', '느림 (100-500ms)', '매우 느림 (>500ms)'],
      datasets: [{
        label: '쿼리 수',
        data: [0, 0, 0, 0],
        backgroundColor: [
          'rgba(75, 192, 192, 0.7)',
          'rgba(54, 162, 235, 0.7)',
          'rgba(255, 206, 86, 0.7)',
          'rgba(255, 99, 132, 0.7)'
        ],
        borderWidth: 1
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      scales: {
        y: {
          beginAtZero: true
        }
      }
    }
  });
}

// 로딩 스피너 표시/숨김 함수
function showLoading() {
  let overlay = document.createElement('div');
  overlay.className = 'loading-overlay';
  overlay.id = 'loadingOverlay';

  let spinner = document.createElement('div');
  spinner.className = 'spinner';

  overlay.appendChild(spinner);
  document.body.appendChild(overlay);
}

function hideLoading() {
  let overlay = document.getElementById('loadingOverlay');
  if (overlay) {
    overlay.remove();
  }
}

// 메트릭 데이터 가져오기
function fetchMetricsData() {
  const selectedService = document.getElementById('serviceSelector').value;
  showLoading();

  // 상태 표시 업데이트
  updateLastUpdated();

  // API 엔드포인트에서 데이터 가져오기
  fetch(`/api/metrics/${selectedService}`)
  .then(response => {
    hideLoading();

    if (!response.ok) {
      throw new Error(`메트릭 데이터를 가져오는 중 오류가 발생했습니다: ${response.statusText}`);
    }
    return response.json();
  })
  .then(data => {
    processMetricsData(data, selectedService);
    updateServiceStatus(data);
    updateDashboard();

    // 로그 데이터도 함께 가져오기
    fetchQueryLogs(selectedService);
  })
  .catch(error => {
    hideLoading();
    console.error('메트릭 데이터를 가져오는 중 오류가 발생했습니다:', error);

    // 오류 발생 시 서비스 상태 업데이트
    updateServiceStatusOnError();

    // 메트릭 데이터를 초기화
    resetMetricsData();

    // 오류 메시지 표시
    const queryLogTable = document.getElementById('queryLogTable');
    queryLogTable.innerHTML = `
        <tr>
          <td colspan="5" class="text-center text-danger">
            <i class="bi bi-exclamation-triangle"></i> 
            서비스에 연결할 수 없습니다: ${error.message}
          </td>
        </tr>
      `;
  });
}

// 쿼리 로그 데이터 가져오기
function fetchQueryLogs(selectedService) {
  fetch(`/api/metrics/logs/${selectedService}`)
  .then(response => {
    if (!response.ok) {
      throw new Error('쿼리 로그 데이터를 가져오는 중 오류가 발생했습니다');
    }
    return response.json();
  })
  .then(data => {
    // 실제 로그 데이터가 있으면 사용
    if (data && data.logs && Array.isArray(data.logs)) {
      // 날짜/시간 기준으로 내림차순 정렬 (최신 로그가 위에 오도록)
      queryLogs = data.logs.sort((a, b) => {
        const timeA = a.time;
        const timeB = b.time;
        return timeB.localeCompare(timeA);
      });

      updateQueryLogTable();

      // 새 로그가 있으면 시각적 알림 추가
      highlightNewLogs();
    }
  })
  .catch(error => {
    console.error('쿼리 로그 데이터를 가져오는 중 오류가 발생했습니다:', error);

    // 오류 메시지 표시
    const tableBody = document.getElementById('queryLogTable');
    tableBody.innerHTML = `
        <tr>
          <td colspan="5" class="text-center text-danger">
            <i class="bi bi-exclamation-triangle"></i> 
            로그 데이터를 가져오는 중 오류가 발생했습니다: ${error.message}
          </td>
        </tr>
      `;
  });
}

// 새 로그 항목 시각적 하이라이트
let lastLogCount = 0;

function highlightNewLogs() {
  const tableBody = document.getElementById('queryLogTable');
  const rows = tableBody.querySelectorAll('tr');

  if (lastLogCount < queryLogs.length) {
    // 새 로그 개수
    const newLogsCount = queryLogs.length - lastLogCount;

    // 최대 5개까지만 하이라이트
    const highlightCount = Math.min(newLogsCount, 5);

    // 하이라이트 추가
    for (let i = 0; i < highlightCount; i++) {
      if (rows[i]) {
        rows[i].classList.add('highlight');
      }
    }

    // 3초 후 하이라이트 제거
    setTimeout(() => {
      rows.forEach(row => row.classList.remove('highlight'));
    }, 3000);
  }

  lastLogCount = queryLogs.length;
}

// 마지막 업데이트 시간 표시
function updateLastUpdated() {
  const now = new Date();
  const timeString = now.toLocaleTimeString();
  document.getElementById('lastUpdated').textContent = `마지막 업데이트: ${timeString}`;
}

// 서비스 상태 업데이트
function updateServiceStatus(data) {
  // API Gateway 상태 업데이트
  let apigatewayOnline = true;
  if (data.apigateway && data.apigateway.error) {
    apigatewayOnline = false;
  }

  // Tech Interview 상태 업데이트
  let techinterviewOnline = true;
  if (data.techinterview && data.techinterview.error) {
    techinterviewOnline = false;
  }

  // 상태 표시기 업데이트
  document.getElementById('apigatewayStatus').className =
      apigatewayOnline ? 'status-indicator status-online' : 'status-indicator status-offline';
  document.getElementById('apigatewayStatusText').textContent =
      apigatewayOnline ? '온라인' : '오프라인';

  document.getElementById('techinterviewStatus').className =
      techinterviewOnline ? 'status-indicator status-online' : 'status-indicator status-offline';
  document.getElementById('techinterviewStatusText').textContent =
      techinterviewOnline ? '온라인' : '오프라인';
}

// 오류 발생 시 서비스 상태 업데이트
function updateServiceStatusOnError() {
  document.getElementById('apigatewayStatus').className = 'status-indicator status-offline';
  document.getElementById('apigatewayStatusText').textContent = '연결 오류';

  document.getElementById('techinterviewStatus').className = 'status-indicator status-offline';
  document.getElementById('techinterviewStatusText').textContent = '연결 오류';
}

// 메트릭 데이터 초기화
function resetMetricsData() {
  queryData = { select: 0, insert: 0, update: 0, delete: 0, other: 0 };
  queryTimeData = { fast: 0, medium: 0, slow: 0, verySlow: 0 };

  // 대시보드 업데이트
  updateDashboard();
}

// API에서 받아온 데이터 처리
function processMetricsData(data, selectedService) {
  // 서비스가 "all"인 경우와 특정 서비스인 경우를 구분하여 처리
  if (selectedService === 'all') {
    // "all" 서비스인 경우 - 집계된 데이터 사용
    queryData.select = data.totalSelectQueries || 0;
    queryData.insert = data.totalInsertQueries || 0;
    queryData.update = data.totalUpdateQueries || 0;
    queryData.delete = data.totalDeleteQueries || 0;
    queryData.other = data.totalOtherQueries || 0;

    queryTimeData.fast = data.totalQueryTimeFast || 0;
    queryTimeData.medium = data.totalQueryTimeMedium || 0;
    queryTimeData.slow = data.totalQueryTimeSlow || 0;
    queryTimeData.verySlow = data.totalQueryTimeVerySlow || 0;
  } else {
    // 특정 서비스인 경우
    queryData.select = data.selectQueries || 0;
    queryData.insert = data.insertQueries || 0;
    queryData.update = data.updateQueries || 0;
    queryData.delete = data.deleteQueries || 0;
    queryData.other = data.otherQueries || 0;

    queryTimeData.fast = data.queryTimeFast || 0;
    queryTimeData.medium = data.queryTimeMedium || 0;
    queryTimeData.slow = data.queryTimeSlow || 0;
    queryTimeData.verySlow = data.queryTimeVerySlow || 0;
  }

  // 현재 시간을 기준으로 타임라인 데이터 추가
  const now = new Date();
  const timeLabel = now.getHours() + ':' +
      (now.getMinutes() < 10 ? '0' : '') + now.getMinutes() + ':' +
      (now.getSeconds() < 10 ? '0' : '') + now.getSeconds();

  timelineData.labels.push(timeLabel);
  timelineData.selectData.push(queryData.select);
  timelineData.insertData.push(queryData.insert);
  timelineData.updateData.push(queryData.update);
  timelineData.deleteData.push(queryData.delete);

  // 최대 10개 포인트만 유지
  if (timelineData.labels.length > 10) {
    timelineData.labels.shift();
    timelineData.selectData.shift();
    timelineData.insertData.shift();
    timelineData.updateData.shift();
    timelineData.deleteData.shift();
  }
}

// 대시보드 업데이트
function updateDashboard() {
  // 요약 지표 업데이트
  document.getElementById('totalQueries').textContent =
      Math.round(queryData.select + queryData.insert + queryData.update + queryData.delete + queryData.other);
  document.getElementById('selectQueries').textContent = Math.round(queryData.select);
  document.getElementById('modifyQueries').textContent =
      Math.round(queryData.insert + queryData.update + queryData.delete);

  // 평균 쿼리 시간 계산
  const totalQueries = queryTimeData.fast + queryTimeData.medium + queryTimeData.slow + queryTimeData.verySlow;
  const avgTime = totalQueries > 0 ?
      Math.round((queryTimeData.fast * 5 + queryTimeData.medium * 50 +
          queryTimeData.slow * 250 + queryTimeData.verySlow * 750) / totalQueries) : 0;
  document.getElementById('avgQueryTime').textContent = avgTime + ' ms';

  // 차트 업데이트
  updateCharts();
}

// 차트 업데이트
function updateCharts() {
  // 쿼리 종류별 차트 업데이트
  queryTypeChart.data.datasets[0].data = [
    Math.round(queryData.select),
    Math.round(queryData.insert),
    Math.round(queryData.update),
    Math.round(queryData.delete),
    Math.round(queryData.other)
  ];
  queryTypeChart.update();

  // 시간별 쿼리 추이 차트 업데이트
  queryTimelineChart.data.labels = timelineData.labels;
  queryTimelineChart.data.datasets[0].data = timelineData.selectData;
  queryTimelineChart.data.datasets[1].data = timelineData.insertData;
  queryTimelineChart.data.datasets[2].data = timelineData.updateData;
  queryTimelineChart.data.datasets[3].data = timelineData.deleteData;
  queryTimelineChart.update();

  // 쿼리 실행 시간 분포 차트 업데이트
  queryTimeDistributionChart.data.datasets[0].data = [
    Math.round(queryTimeData.fast),
    Math.round(queryTimeData.medium),
    Math.round(queryTimeData.slow),
    Math.round(queryTimeData.verySlow)
  ];
  queryTimeDistributionChart.update();
}

// 쿼리 로그 테이블 업데이트
function updateQueryLogTable() {
  const tableBody = document.getElementById('queryLogTable');
  const queryFilter = document.getElementById('queryFilter').value;
  tableBody.innerHTML = '';

  // 필터링된 로그 생성
  const filteredLogs = queryLogs.filter(log => {
    if (queryFilter === 'slow') {
      return log.executionTime >= 100; // 100ms 이상인 쿼리만 표시
    }
    return true; // 모든 쿼리 표시
  });

  if (filteredLogs.length === 0) {
    tableBody.innerHTML = `
      <tr>
        <td colspan="5" class="text-center">
          <i class="bi bi-info-circle"></i> 표시할 로그가 없습니다
        </td>
      </tr>
    `;
    return;
  }

  filteredLogs.forEach(log => {
    const row = document.createElement('tr');

    // 쿼리 종류에 따른 색상 클래스 설정
    let typeClass = '';
    switch(log.type) {
      case 'SELECT': typeClass = 'text-primary'; break;
      case 'INSERT': typeClass = 'text-success'; break;
      case 'UPDATE': typeClass = 'text-warning'; break;
      case 'DELETE': typeClass = 'text-danger'; break;
    }

    // 실행 시간에 따른 색상 클래스 설정
    let timeClass = '';
    if (log.executionTime < 10) {
      timeClass = 'text-success';
    } else if (log.executionTime < 100) {
      timeClass = 'text-info';
    } else if (log.executionTime < 500) {
      timeClass = 'text-warning';
    } else {
      timeClass = 'text-danger fw-bold';
    }

    // SQL 쿼리 포맷팅
    const formattedSql = formatSql(log.sql);

    row.innerHTML = `
      <td>${log.time}</td>
      <td>${log.service}</td>
      <td class="${typeClass}">${log.type}</td>
      <td class="${timeClass}">${log.executionTime} ms</td>
      <td>
        <div class="sql-container">
          <pre class="sql-code m-0 p-0">${escapeHtml(formattedSql)}</pre>
          <button class="btn btn-sm btn-outline-secondary copy-btn" data-sql="${escapeHtml(log.sql)}">복사</button>
        </div>
      </td>
    `;

    tableBody.appendChild(row);
  });

  // 복사 버튼에 이벤트 리스너 추가
  document.querySelectorAll('.copy-btn').forEach(button => {
    button.addEventListener('click', function() {
      const sql = this.getAttribute('data-sql');
      navigator.clipboard.writeText(sql)
      .then(() => {
        // 복사 성공 표시
        const originalText = this.textContent;
        this.textContent = '복사됨!';
        this.classList.remove('btn-outline-secondary');
        this.classList.add('btn-success');

        // 2초 후 원래 상태로 복원
        setTimeout(() => {
          this.textContent = originalText;
          this.classList.remove('btn-success');
          this.classList.add('btn-outline-secondary');
        }, 2000);
      })
      .catch(err => {
        console.error('클립보드 복사 실패:', err);
      });
    });
  });
}

// SQL 쿼리 포맷팅 함수
function formatSql(sql) {
  if (!sql) return '';

  // 기본적인 SQL 키워드 대문자화 및 줄바꿈 추가
  return sql
  .replace(/\s+/g, ' ')
  .replace(/(\bSELECT\b|\bFROM\b|\bWHERE\b|\bJOIN\b|\bLEFT JOIN\b|\bRIGHT JOIN\b|\bINNER JOIN\b|\bGROUP BY\b|\bORDER BY\b|\bHAVING\b|\bLIMIT\b)/gi,
      match => '\n' + match.toUpperCase())
  .replace(/(\,)/g, '$1\n  ')
  .trim();
}

// HTML 이스케이프 함수
function escapeHtml(unsafe) {
  return unsafe
  .replace(/&/g, "&amp;")
  .replace(/</g, "&lt;")
  .replace(/>/g, "&gt;")
  .replace(/"/g, "&quot;")
  .replace(/'/g, "&#039;");
}