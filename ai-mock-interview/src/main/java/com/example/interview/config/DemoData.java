package com.example.interview.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.domain.*;
import com.example.interview.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
@RequiredArgsConstructor
public class DemoData implements ApplicationRunner {
  private final UserMapper users;
  private final QuestionMapper questions;
  private final PasswordEncoder passwords;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    seedUser("admin", "INTERVIEWER");
    seedUser("candidate", "CANDIDATE");

    String[][] entries = {
      {
        "Java 中 equals 与 hashCode 有什么关系？放入 HashMap 时会发生什么？",
        "说明自反性、对称性、传递性；相等对象必须有相同 hashCode；HashMap 先定位桶再比较键。"
      },
      {
        "解释 Java 集合中 ArrayList 和 LinkedList 的差异，你会如何选择？",
        "比较连续数组和链表、随机访问、插入删除、扩容与局部性；不要把链表插入笼统说成总是 O(1)。"
      },
      {
        "什么是线程安全？synchronized 与 ReentrantLock 分别适合什么情况？",
        "互斥、可见性和有序性；synchronized 自动释放；Lock 支持可中断、超时、公平锁，需 finally 解锁。"
      },
      {"volatile 可以替代锁吗？请结合 i++ 举例说明。", "volatile 保证可见性及部分有序性，不保证复合操作原子性；可使用原子类或同步保护 i++。"},
      {
        "介绍 Spring IOC 和 AOP，并举出你会在项目中使用它们的场景。", "IOC 管理对象与依赖；AOP 通过代理处理横切关注点；日志、权限、事务；说明自调用可能绕过代理。"
      },
      {"Spring 声明式事务在哪些情况下会失效？如何定位？", "事务方法通过代理调用；默认回滚运行时异常；自调用、异常被吞、错误传播配置；结合日志和数据库状态排查。"},
      {"MySQL 联合索引 (a,b,c) 的最左前缀原则是什么？", "从最左列开始匹配；范围查询影响后续列利用；结合 EXPLAIN 和具体查询，不把条件书写顺序当作索引顺序。"},
      {"请比较 MySQL 的可重复读和读已提交。", "解释快照、MVCC、事务读视图建立时机、不可重复读；当前读和快照读区分；间隙锁不是所有查询都有。"},
      {"如何处理 Redis 缓存穿透、击穿和雪崩？", "分别解释无效键、热点失效、集中失效；空值或布隆、互斥重建、TTL 随机化，并说明取舍。"},
      {"数据库更新后如何处理缓存？先删除还是先更新？", "常见 Cache Aside 先更新数据库再删除缓存；分析并发窗口、失败重试、TTL；承认一致性边界。"},
      {"RabbitMQ 消费者如何避免一条消息被重复处理？", "消息至少一次投递；业务唯一键、数据库唯一约束或状态 CAS 保证幂等；事务完成再确认。"},
      {"你会如何设计一个防止重复提交的订单接口？", "区分前端禁用与后端幂等；唯一业务键、数据库约束、状态机；说明请求重试和并发提交。"},
      {"线上接口突然变慢，你会按什么顺序排查？", "确认影响范围与时间线；分解网络、应用、SQL、外部依赖耗时；结合监控日志和执行计划，不盲目加缓存。"},
      {"描述你处理过的一个技术问题。如果缺少经验，你会怎样验证解决方案？", "给出问题背景、假设、复现、方案、验证与反思；没有经历可诚实给出模拟验证路径。"},
      {"如果项目进度紧张，同时发现了一个数据一致性问题，你会怎样处理？", "说明风险评估、最小正确修复、沟通和验证；不以赶进度掩盖已知数据错误。"}
    };
    for (int index = 0; index < entries.length; index++) {
      var matches =
          questions.selectList(
              Wrappers.<Question>lambdaQuery().eq(Question::getContent, entries[index][0]));
      var q = matches.isEmpty() ? new Question() : matches.get(0);
      if (!matches.isEmpty()
          && (!",未分类,".equals(q.getTags()) || !"APPROVED".equals(q.getReviewStatus()))) continue;
      String[] tags = {
        "Java,集合",
        "Java,集合",
        "Java,并发",
        "Java,并发",
        "Spring,代理",
        "Spring,事务",
        "MySQL,索引",
        "MySQL,事务",
        "Redis,缓存",
        "Redis,缓存",
        "RabbitMQ,幂等",
        "接口设计,幂等",
        "问题排查",
        "项目实践",
        "团队协作"
      };
      q.setJobFamily("JAVA_BACKEND");
      q.setTags("," + tags[index] + ",");
      q.setReviewStatus("APPROVED");
      q.setPosition("Java 后端");
      q.setContent(entries[index][0]);
      q.setReferenceAnswer(entries[index][1]);
      q.setDifficulty(index < 5 ? "EASY" : index < 10 ? "MEDIUM" : "HARD");
      q.setCategory(index >= 13 ? "BEHAVIORAL" : index >= 11 ? "SCENARIO" : "TECHNICAL");
      if (q.getId() == null) questions.insert(q);
      else questions.updateById(q);
    }
    String[][] extras = {
      {
        "JAVA_BACKEND",
        "Spring",
        "Spring Bean 单例是否意味着线程安全？请给出一个有共享状态的例子。",
        "区分单例生命周期与并发安全；避免请求数据放入实例字段，使用局部变量或正确同步。"
      },
      {
        "JAVA_BACKEND",
        "Spring,事务",
        "同一事务方法抛出受检异常时默认会回滚吗？如何验证 rollbackFor？",
        "默认回滚 RuntimeException 和 Error；受检异常通常不回滚，可配置 rollbackFor，并通过数据库结果验证。"
      },
      {
        "JAVA_BACKEND",
        "Spring,代理",
        "JDK 动态代理与 CGLIB 代理各有什么限制？",
        "接口代理与子类代理的区别；final 方法无法被子类覆写；通过 Spring 代理调用才进入增强。"
      },
      {
        "JAVA_BACKEND",
        "MySQL,索引",
        "EXPLAIN 的 key、rows、Extra 字段分别能帮助发现什么问题？",
        "key 是选用索引，rows 为估算扫描行；Extra 可提示覆盖索引、排序等，需结合真实执行和数据分布。"
      },
      {
        "JAVA_BACKEND", "MySQL,事务", "两个事务更新同两行但顺序相反，为什么可能死锁？", "持锁互相等待；统一访问顺序、缩短事务，识别死锁后按业务幂等性有限重试。"
      },
      {"JAVA_BACKEND", "Redis,缓存", "热点缓存重建时，如何让并发请求避免同时访问数据库？", "互斥重建、逻辑过期或请求合并；解释等待、旧值和失败恢复的取舍。"},
      {
        "JAVA_BACKEND",
        "Redis,缓存",
        "缓存空值与布隆过滤器各自有哪些成本和适用边界？",
        "空值占内存且有短暂不一致，布隆存在假阳性并需要更新；结合无效请求规模和数据变化选择。"
      },
      {
        "JAVA_BACKEND",
        "Java,并发",
        "线程池的核心线程、队列和最大线程如何配合？",
        "任务提交先核心线程，再队列，队列满后扩到最大线程，最终拒绝；结合工作负载而非盲目增大。"
      },
      {
        "JAVA_BACKEND",
        "Java,集合",
        "HashMap 并发读写有什么风险，什么场景使用 ConcurrentHashMap？",
        "普通 HashMap 不保证并发可见性与原子性；并发容器单次操作与复合操作应区分，可用 compute 等原子接口。"
      },
      {
        "JAVA_BACKEND",
        "RabbitMQ,幂等",
        "业务处理成功但确认消息前连接断开，消费者应怎样处理再次投递？",
        "持久化业务唯一键或状态 CAS；重复投递查询已有结果并确认，不依赖内存去重。"
      },
      {
        "JAVA_BACKEND",
        "接口设计,幂等",
        "客户端请求超时但服务端可能已创建订单，重试会遇到什么问题？",
        "超时不代表失败，需业务幂等键、唯一约束和可查询结果，处理并发重复与部分失败。"
      },
      {
        "JAVA_BACKEND",
        "问题排查",
        "慢 SQL 与外部 API 慢分别如何在日志或链路耗时中区分？",
        "按请求关联各步骤耗时，检查数据库执行计划与外部响应，先定位瓶颈再改变架构。"
      },
      {
        "FRONTEND",
        "Vue,响应式",
        "Vue 中 computed 和 watch 分别适合处理什么？",
        "computed 用于派生值并缓存，watch 处理变化后的副作用，避免无谓复制状态和循环更新。"
      },
      {
        "FRONTEND",
        "JavaScript,异步",
        "Promise 的错误传播和 async/await 的 try/catch 有什么关系？",
        "await 拒绝进入 catch；漏写 await 可能逃逸当前 catch；区分并行请求与顺序依赖。"
      },
      {
        "FRONTEND",
        "浏览器,HTTP",
        "跨域请求为什么会产生预检，服务端应怎样响应？",
        "区分同源策略、简单请求与预检，按允许来源、方法、Header 配置 CORS，不能将跨域当认证。"
      },
      {"TESTING", "测试设计,边界", "为一个分页查询接口设计测试时，你会覆盖哪些边界？", "有效页、空结果、页大小限制、非法参数、排序稳定性、权限数据隔离。"},
      {
        "TESTING", "测试设计,幂等", "如何验证订单接口在重复和并发提交时只创建一个订单？", "设计同幂等键串行与并发请求，验证状态与数据库唯一记录，并测试超时后的查询重试。"
      },
      {"GENERAL", "项目实践", "描述一次你从失败中改进方案的过程，没有工作经验也可以使用课程项目。", "说明背景、本人行动、具体结果与复盘，诚实区分实际经历和假设。"}
    };
    for (var item : extras) {
      if (questions.selectCount(Wrappers.<Question>lambdaQuery().eq(Question::getContent, item[2]))
          > 0) continue;
      var q = new Question();
      q.setJobFamily(item[0]);
      q.setPosition(
          switch (item[0]) {
            case "FRONTEND" -> "前端开发";
            case "TESTING" -> "软件测试";
            case "GENERAL" -> "通用岗位";
            default -> "Java 后端";
          });
      q.setTags("," + item[1] + ",");
      q.setContent(item[2]);
      q.setReferenceAnswer(item[3]);
      q.setDifficulty("MEDIUM");
      q.setCategory("GENERAL".equals(item[0]) ? "BEHAVIORAL" : "TECHNICAL");
      q.setReviewStatus("APPROVED");
      questions.insert(q);
    }
  }

  private void seedUser(String username, String role) {
    if (users.selectCount(Wrappers.<User>lambdaQuery().eq(User::getUsername, username)) != 0)
      return;
    var user = new User();
    user.setUsername(username);
    user.setPassword(passwords.encode("Interview123!"));
    user.setRole(role);
    users.insert(user);
  }
}
