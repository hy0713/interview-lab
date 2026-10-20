param([string]$BaseUrl='http://127.0.0.1:3000')
$ErrorActionPreference='Stop'
$workspace=Split-Path $PSScriptRoot -Parent
$results=[ordered]@{checkedAt=[DateTimeOffset]::Now.ToString('o');checks=@();sessions=@()}
function Check([bool]$ok,[string]$message){if(-not $ok){throw $message};$script:results.checks+=$message;Write-Output ('PASS '+$message)|Out-Host}
function Send([string]$method,[string]$path,$body=$null,[hashtable]$headers=@{}){
 $request=@{Uri=$BaseUrl+'/api'+$path;Method=$method;Headers=$headers;TimeoutSec=210}
 if($null-ne $body){$request.ContentType='application/json; charset=utf-8';$request.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 30))}
 return (Invoke-RestMethod @request).data
}
function Await-Report([long]$id,[hashtable]$headers){
 $until=[DateTime]::UtcNow.AddMinutes(4)
 while([DateTime]::UtcNow -lt $until){
  $state=Send 'GET' ('/interviews/'+$id) $null $headers
  if($state.failures.Count){throw ('AI task failed: '+($state.failures.message -join '; '))}
  if($state.interview.status -eq 'COMPLETED'){return $state}
  if($state.interview.phase -eq 'READY'){throw ('Unexpected follow-up on complete answer: '+$id)}
  Start-Sleep -Milliseconds 900
 }
 throw ('Report timed out: '+$id)
}
try {
 $adminSession=Send 'POST' '/auth/login' @{username='admin';password='Interview123!'}
 $candidateSession=Send 'POST' '/auth/login' @{username='candidate';password='Interview123!'}
 $admin=@{Authorization='Bearer '+$adminSession.token};$candidate=@{Authorization='Bearer '+$candidateSession.token}
 $runId=[DateTime]::Now.ToString('MMddHHmmss');$position='P1-事务复练-'+$runId;$tag='P1事务'+$runId
 $q=Send 'POST' '/questions' @{position=$position;content='Spring 默认代理模式下，同类内部调用 @Transactional 方法为什么可能不生效？如何处理并验证？';referenceAnswer='自调用绕过代理，事务拦截器无法执行。考虑外层已有事务、传播行为、AspectJ 等边界；拆分为另一个 Bean 通过注入调用。开启事务日志，用数据库回滚结果验证，并检查异常类型和 rollbackFor。';category='TECHNICAL';difficulty='MEDIUM';jobFamily='JAVA_BACKEND';tags=@('Spring',$tag)} $admin
 Check ($q.reviewStatus -eq 'DRAFT') '新题保存为草稿'
 $hidden=Send 'GET' ('/questions?keyword='+[Uri]::EscapeDataString($position)) $null $candidate
 Check ($hidden.total -eq 0) '候选人看不到未审核题目'
 [void](Send 'POST' ('/questions/'+$q.id+'/review') @{status='APPROVED';note='P1 功能验收，题干与参考要点已检查'} $admin)
 $visible=Send 'GET' ('/questions?tag='+[Uri]::EscapeDataString($tag)) $null $candidate
 Check ($visible.total -eq 1 -and $null -eq $visible.records[0].referenceAnswer) '审核发布后按标签可查，候选人无参考答案'
 [void](Send 'PUT' ('/questions/'+$q.id+'/favorite') $null $candidate)
 [void](Send 'PUT' ('/questions/'+$q.id+'/favorite') $null $candidate)
 $favorites=Send 'GET' ('/questions?favorites=true&tag='+[Uri]::EscapeDataString($tag)) $null $candidate
 Check ($favorites.total -eq 1 -and $favorites.records[0].favorite) '重复收藏幂等且可筛选'
 $assigned=Send 'POST' '/interviews' @{position=$position;jobDescription='P1 真实复练验收';difficulty='MEDIUM';source='BANK';count=1;secondsPerQuestion=180;mode='PRACTICE';maxFollowUps=0;candidateId=$candidateSession.user.id;jobFamily='JAVA_BACKEND';tag=$tag} $admin
 $created=Send 'GET' ('/interviews/'+$assigned.id) $null $candidate
 Check ($created.interview.candidateId -eq $candidateSession.user.id -and $created.interview.interviewerId -eq $adminSession.user.id) '面试官按知识点组卷并指派候选人'
 try {[void](Send 'POST' ('/interviews/'+$assigned.id+'/start') $null $admin);throw 'Interviewer started candidate interview'}catch{Check ([int]$_.Exception.Response.StatusCode -eq 403) '面试官不能替候选人作答'}
 $started=Send 'POST' ('/interviews/'+$assigned.id+'/start') $null $candidate
 $first=$started.turns[0]
 [void](Send 'POST' ('/interviews/'+$assigned.id+'/turns/'+$first.id+'/answer') @{answer='我认为在方法上加 @Transactional 就保证事务生效，内部调用也一样。我还不清楚如何验证回滚。'} $candidate)
 $original=Await-Report $assigned.id $candidate
 Check ($original.turns[0].questionId -eq $q.id -and $original.turns[0].tags.Contains($tag)) '报告保留题库来源与知识点快照'
 $aiScore=$original.interview.totalScore
 [void](Send 'PUT' ('/interviews/'+$assigned.id+'/review') @{score=75;comment='P1 验收人工评语：需要结合代理边界和实际回滚实验继续练习。此评语为测试数据。'} $admin)
 $human=Send 'GET' ('/interviews/'+$assigned.id+'/review') $null $candidate
 $unchanged=Send 'GET' ('/interviews/'+$assigned.id) $null $candidate
 Check ($human.score -eq 75 -and $unchanged.interview.totalScore -eq $aiScore) '人工评语可见且不覆盖 AI 分数'
 $retry=Send 'POST' ('/learning/turns/'+$first.id+'/practice') $null $candidate
 $retryState=Send 'POST' ('/interviews/'+$retry.id+'/start') $null $candidate
 $next=$retryState.turns[0]
 Check ($next.content -eq $first.content -and $next.retryOfTurnId -eq $first.id) '复练沿用原题快照并关联前次回答'
 [void](Send 'POST' ('/interviews/'+$retry.id+'/turns/'+$next.id+'/answer') @{answer='Spring 声明式事务默认通过 AOP 代理生效，外部调用经过代理才进入事务拦截器。同类内部 this 调用不会经过代理，因此目标方法上的事务配置没有机会执行。但如果外层已开启事务，内部操作仍可能处于外层事务中；REQUIRES_NEW 等传播配置在自调用时也不会被拦截应用。可以将事务方法拆到另一个 Spring 管理的 Bean，再通过注入调用；AspectJ 模式则通过织入增强处理，是另一种机制。验证时应开启事务日志，使用 TransactionSynchronizationManager 检查实际事务状态，并构造真实数据库写入后抛出异常的测试，验证提交或回滚结果；默认 RuntimeException 和 Error 回滚，受检异常需注意 rollbackFor，不能只看方法上是否有注解。'} $candidate)
 $improved=Await-Report $retry.id $candidate
 $history=Send 'GET' ('/learning/turns/'+$first.id+'/history') $null $candidate
 Check (@($history|Where-Object id -EQ $first.id).Count -eq 1 -and @($history|Where-Object id -EQ $next.id).Count -eq 1) '同题历史包含前后真实回答'
 Check ($improved.turns[0].score -gt $original.turns[0].score) ('复练评分体现回答改善：'+$original.turns[0].score+' → '+$improved.turns[0].score)
 $mistakes=Send 'GET' ('/learning/mistakes?keyword='+[Uri]::EscapeDataString($position)) $null $candidate
 Check (@($mistakes.records|Where-Object id -EQ $first.id).Count -eq 1) '低分原回答进入个人错题列表'
 $overview=Send 'GET' ('/learning/overview?position='+[Uri]::EscapeDataString($position)) $null $candidate
 Check ($overview.trend.Count -eq 2 -and @($overview.weakTags|Where-Object name -EQ $tag).Count -eq 1) '成长趋势与知识点评分来自真实已完成面试'
 $bench=Send 'GET' ('/management/workbench?keyword='+[Uri]::EscapeDataString($position)) $null $admin
 Check ($bench.total -eq 2 -and @($bench.records|Where-Object {$_.id -eq $assigned.id -and $_.human_score -eq 75}).Count -eq 1) '面试官工作台显示候选人、评分进度与人工分'
 foreach($state in @($original,$improved)){foreach($turn in $state.turns){foreach($e in $turn.evaluation.evidence){Check ($turn.answer.Contains($e.quote)) 'P1 真实评分引用匹配回答原文'}}}
 [void](Send 'DELETE' ('/questions/'+$q.id+'/favorite') $null $candidate)
 $results.sessions=@($original,$improved);$results.passed=$true
} catch {$results.passed=$false;$results.failure=$_.Exception.Message;throw}
finally {[IO.File]::WriteAllText((Join-Path $workspace '.local/p1-live-results.json'),($results|ConvertTo-Json -Depth 50),[Text.UTF8Encoding]::new($false))}
