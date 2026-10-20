param([string]$BaseUrl='',[long]$ReusePartialId=0)
$ErrorActionPreference='Stop'
$workspace=Split-Path $PSScriptRoot -Parent
if (-not $BaseUrl) {
    $backendPort = 8080
    $envFile = Join-Path $workspace '.env'
    if (Test-Path -LiteralPath $envFile) {
        foreach ($line in [IO.File]::ReadAllLines($envFile)) {
            if ($line -match '^BACKEND_PORT=(\d+)$') { $backendPort = $Matches[1] }
        }
    }
    $BaseUrl = 'http://127.0.0.1:' + $backendPort
}
$results=[ordered]@{checkedAt=[DateTimeOffset]::Now.ToString('o');checks=@();sessions=@()}
function Check([bool]$Condition,[string]$Message) {
    if(-not $Condition){throw $Message}
    $script:results.checks += $Message
    Write-Output ('PASS '+$Message) | Out-Host
}
function Send([string]$Method,[string]$Path,$Payload=$null,[hashtable]$Headers=@{}) {
    $parameters=@{Uri=$BaseUrl+'/api'+$Path;Method=$Method;Headers=$Headers;TimeoutSec=210}
    if($null -ne $Payload) {
        $parameters.ContentType='application/json; charset=utf-8'
        $parameters.Body=[Text.Encoding]::UTF8.GetBytes(($Payload|ConvertTo-Json -Depth 30))
    }
    return (Invoke-RestMethod @parameters).data
}
function Login([string]$Username) {
    $session=Send 'POST' '/auth/login' @{username=$Username;password='Interview123!'}
    return @{Authorization='Bearer '+$session.token}
}
function Await-Analysis([long]$Id,[hashtable]$Headers) {
    $until=[DateTime]::UtcNow.AddMinutes(4)
    while([DateTime]::UtcNow -lt $until) {
        $state=Send 'GET' ('/interviews/'+$Id) $null $Headers
        if(@($state.failures).Count -gt 0) {throw ('评估失败：'+($state.failures.message -join '; '))}
        if($state.interview.status -eq 'COMPLETED' -or $state.interview.phase -eq 'READY'){return $state}
        Start-Sleep -Milliseconds 900
    }
    throw ('等待评估超时，interviewId='+$Id)
}
function Run-Interview([string]$Position,[string]$Answer,[string]$FollowUpAnswer,[hashtable]$Headers,[int]$MaxFollowUps=1) {
    $created=Send 'POST' '/interviews' @{position=$Position;jobDescription='大学生 Java 后端岗位专项面试';difficulty='MEDIUM';source='BANK';count=1;secondsPerQuestion=180;mode='PRACTICE';maxFollowUps=$MaxFollowUps} $Headers
    $state=Send 'POST' ('/interviews/'+$created.id+'/start') $null $Headers
    $turn=@($state.turns|Where-Object status -EQ 'ACTIVE')[0]
    [void](Send 'POST' ('/interviews/'+$created.id+'/turns/'+$turn.id+'/answer') @{answer=$Answer} $Headers)
    Write-Output ('Waiting for live AI assessment: interview '+$created.id) | Out-Host
    $state=Await-Analysis $created.id $Headers
    if($state.interview.phase -eq 'READY') {
        Check (@($state.turns|Where-Object status -EQ 'ACTIVE').Count -eq 0) 'AI 分析后等待用户确认，尚未启动追问计时'
        $state=Send 'POST' ('/interviews/'+$created.id+'/next') $null $Headers
        $child=@($state.turns|Where-Object status -EQ 'ACTIVE')[0]
        Check ($child.depth -eq 1 -and $null -ne $child.parentTurnId) '追问关联原主问题'
        [void](Send 'POST' ('/interviews/'+$created.id+'/turns/'+$child.id+'/answer') @{answer=$FollowUpAnswer} $Headers)
        $state=Await-Analysis $created.id $Headers
    }
    Check ($state.interview.status -eq 'COMPLETED') ('真实 MQ 评分与报告完成：'+$created.id)
    foreach($item in $state.turns) {
        Check ($item.evaluation.dimensions.Count -eq 3) ('评分包含三个维度：'+$item.id)
        foreach($evidence in $item.evaluation.evidence) {
            Check ($item.answer.Contains($evidence.quote)) ('评分引用确实来自本轮回答：'+$item.id)
        }
    }
    $script:results.sessions += $state
    return $state
}
try {
    $admin=Login 'admin'
    $candidate=Login 'candidate'
    $status=Send 'GET' '/system/status' $null $admin
    Check ($status.database -and $status.redis -and $status.rabbitmq) '真实 MySQL、Redis、RabbitMQ 连接可用'
    $probe=Send 'POST' '/system/ai-check' $null $admin
    Check ($probe.available -eq $true) ('Spring AI 实际请求 DeepSeek 成功：'+$probe.model)

    $position='P0-事务专项'
    $existing=Send 'GET' '/questions?keyword=P0-' $null $admin
    if(@($existing.records|Where-Object position -EQ $position).Count -eq 0) {
        $question=Send 'POST' '/questions' @{position=$position;content='同一个类内部调用标有 @Transactional 的方法，事务为什么可能不生效？你会如何验证和处理？';category='TECHNICAL';difficulty='MEDIUM';referenceAnswer='默认代理模式下，自调用绕过 Spring 代理，没有进入事务拦截器。需区分外层已有事务、传播行为及 AspectJ 等条件。可拆分到另一个 Spring Bean，通过注入调用；用事务日志和真实回滚验证，不能只看注解。'} $admin
        [void](Send 'POST' ('/questions/'+$question.id+'/review') @{status='APPROVED';note='P0 事务专项验收题'} $admin)
    }

    $timed=Send 'POST' '/interviews' @{position='Java 后端';jobDescription='真实计时联调';difficulty='MEDIUM';source='BANK';count=2;secondsPerQuestion=60;mode='EXAM';maxFollowUps=0} $candidate
    $timedState=Send 'POST' ('/interviews/'+$timed.id+'/start') $null $candidate
    $firstTimed=@($timedState.turns|Where-Object status -EQ 'ACTIVE')[0]
    try {
        [void](Send 'POST' ('/interviews/'+$timed.id+'/pause') $null $candidate)
        throw '模拟模式错误地允许了暂停'
    } catch {
        Check ([int]$_.Exception.Response.StatusCode -eq 409) '模拟模式拒绝暂停当前题'
    }

    $practice=Send 'POST' '/interviews' @{position=$position;jobDescription='暂停恢复联调';difficulty='MEDIUM';source='BANK';count=1;secondsPerQuestion=60;mode='PRACTICE';maxFollowUps=0} $candidate
    [void](Send 'POST' ('/interviews/'+$practice.id+'/start') $null $candidate)
    $paused=Send 'POST' ('/interviews/'+$practice.id+'/pause') $null $candidate
    Check ($paused.interview.status -eq 'PAUSED' -and $null -eq $paused.turns[0].deadline -and $paused.turns[0].remainingMillis -gt 0) '暂停将剩余时间保存至真实数据库并移除 deadline'
    $resumed=Send 'POST' ('/interviews/'+$practice.id+'/resume') $null $candidate
    Check ($resumed.interview.status -eq 'IN_PROGRESS' -and $null -ne $resumed.turns[0].deadline) '恢复重新建立答题期限'
    [void](Send 'POST' ('/interviews/'+$practice.id+'/cancel') $null $candidate)

    if($ReusePartialId -gt 0) {
        $partial=Send 'GET' ('/interviews/'+$ReusePartialId) $null $candidate
        Check ($partial.interview.status -eq 'COMPLETED') '复用已完成的真实不完整回答样本，避免重复付费调用'
        $results.sessions += $partial
    } else {
        $partial=Run-Interview $position '因为 Spring 是通过注解控制事务，在方法上加 @Transactional 就能保证事务。我还没有验证过同类内部调用的情况。' '默认代理模式下，同类内部调用没有经过代理，事务拦截器无法介入。我会把需要独立事务的方法拆到另一个 Spring Bean，通过注入调用。还要检查外层是否已有事务及传播行为，不能把自调用等同于一定没有事务。验证时开启事务日志，并在测试中写入后抛出运行时异常，检查数据库是否回滚。' $candidate
    }
    $complete=Run-Interview $position 'Spring 声明式事务默认依赖 AOP 代理。调用方通过 Spring 代理进入方法时，事务拦截器才会按传播行为开启或加入事务。同类内部通过 this 调用不会经过代理，因此被调用方法上的事务配置可能没有机会执行。不过如果外层已经开启事务，内部逻辑仍可能运行在该事务中，不能笼统地说完全没有事务。可以把事务方法拆到另一个由 Spring 管理的 Bean，通过注入调用；AspectJ 模式则是另一种机制。验证时我会开启事务日志，在集成测试中检查事务是否激活，构造写入后抛出异常的场景，再从数据库验证回滚结果，并检查异常类型及 rollbackFor 配置。' '' $candidate
    $partialRoot=@($partial.turns|Where-Object depth -EQ 0)[0]
    $completeRoot=@($complete.turns|Where-Object depth -EQ 0)[0]
    Check (@($partial.turns|Where-Object depth -GT 0).Count -eq 1) '不完整回答触发了真实 AI 追问'
    Check (@($complete.turns|Where-Object depth -GT 0).Count -eq 0) '完整回答未被机械追加追问'
    Check ($completeRoot.score -gt $partialRoot.score) ('回答差异反映在评分中：不完整 '+$partialRoot.score+'，完整 '+$completeRoot.score)

    $generated=Send 'POST' '/interviews' @{position='Java 后端';jobDescription='针对应届生，生成一道 Spring Bean 生命周期问题';difficulty='MEDIUM';source='AI';count=1;secondsPerQuestion=60;mode='PRACTICE';maxFollowUps=0} $candidate
    $generatedState=Send 'POST' ('/interviews/'+$generated.id+'/start') $null $candidate
    Check (-not [string]::IsNullOrWhiteSpace($generatedState.turns[0].content)) '真实 DeepSeek 定制出题已保存为面试快照'
    $script:results.generatedQuestion=$generatedState.turns[0].content
    [void](Send 'POST' ('/interviews/'+$generated.id+'/cancel') $null $candidate)

    $deadline=[DateTime]::Parse($firstTimed.deadline).AddSeconds(12)
    while([DateTime]::Now -lt $deadline) {Start-Sleep -Seconds 1}
    $expired=Send 'GET' ('/interviews/'+$timed.id) $null $candidate
    Check ($expired.turns[0].status -eq 'TIMEOUT' -and $expired.interview.phase -eq 'READY') '真实 Redis 到期扫描处理了当前题超时'
    Check ($expired.turns[1].status -eq 'WAITING' -and $null -eq $expired.turns[1].deadline) '离开页面后不会连续开启后续题目计时'
    [void](Send 'POST' ('/interviews/'+$timed.id+'/cancel') $null $candidate)

    [void](Send 'POST' '/auth/logout' $null $candidate)
    try { [void](Send 'GET' '/auth/me' $null $candidate);throw '旧 JWT 未失效' }
    catch { Check ([int]$_.Exception.Response.StatusCode -eq 401) '真实 Redis 撤销列表使登出 JWT 失效' }
    $results.passed=$true
} catch {
    $results.passed=$false;$results.failure=$_.Exception.Message
    throw
} finally {
    $output=Join-Path $workspace '.local/p0-live-results.json'
    [IO.File]::WriteAllText($output,($results|ConvertTo-Json -Depth 50),[Text.UTF8Encoding]::new($false))
    Write-Output ('Results saved to '+$output) | Out-Host
}
