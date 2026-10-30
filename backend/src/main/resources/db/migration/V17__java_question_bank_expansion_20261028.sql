INSERT INTO question_bank
(category, level, tags, difficulty, question, reference_answer, source, status, created_at, updated_at)
VALUES
('Java 基础', '校招', 'Java,Java17,新特性', 2, 'Java 17 相比 Java 8 有哪些值得关注的新特性？', 'Java 17 是 LTS 版本，常见变化包括 record 简化不可变数据载体、sealed 限制继承体系、switch 模式匹配预览、文本块、instanceof 模式匹配，以及更强的 GC 和容器支持。回答时结合项目说明为什么升级、兼容性风险和对性能的影响。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 基础', '1-3 年', 'Java,泛型,PECS', 2, '什么是泛型 PECS 原则？什么时候用 extends，什么时候用 super？', 'PECS 指 Producer Extends、Consumer Super。生产者只向外提供元素时用 extends，消费者只接收元素时用 super。典型例子是 Collections.copy 的参数设计。原则的核心是保证类型安全，同时提高 API 的灵活性。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 基础', '1-3 年', 'Java,反射,框架', 2, '反射的优缺点是什么？框架中为什么大量使用反射？', '反射可以在运行时获取类信息、创建对象、调用方法和访问字段，是 Spring、MyBatis 等框架实现依赖注入、映射和代理的基础。缺点是性能开销、破坏封装、编译期无法发现错误，且可能带来安全问题。高频场景应缓存反射结果或使用 MethodHandle。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 基础', '1-3 年', 'Java,动态代理,JDK,CGLIB', 2, 'JDK 动态代理和 CGLIB 动态代理有什么区别？Spring 如何选择？', 'JDK 动态代理基于接口，通过 Proxy 和 InvocationHandler 生成实现类；CGLIB 通过继承生成子类，可以代理普通类，但不能代理 final 类和 final 方法。Spring AOP 默认有接口时用 JDK，没有接口时用 CGLIB，也可以通过 proxyTargetClass 强制使用 CGLIB。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 基础', '校招', 'Java,异常,受检异常', 1, '受检异常和非受检异常有什么区别？实际项目应该怎么选？', '受检异常继承 Exception，编译器要求调用方捕获或声明；非受检异常继承 RuntimeException，不强制处理。业务代码通常用非受检异常表达参数错误、状态冲突等，框架层统一转换成错误码；受检异常适合调用方确实能够恢复的外部资源错误。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 基础', '1-3 年', 'Java,SPI,扩展机制', 2, 'Java SPI 是什么？它和 API 有什么区别？', 'SPI 是服务提供者接口，由接口方定义规范、实现方提供实现，通过 META-INF/services 或 ServiceLoader 在运行时发现实现。API 是调用方依赖提供方，SPI 是框架依赖调用方提供扩展。JDBC 驱动、日志门面都使用了类似机制。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 集合', '校招', 'Java,集合,ArrayList,LinkedList', 1, 'ArrayList 和 LinkedList 分别适合什么场景？', 'ArrayList 基于动态数组，随机访问快，尾部追加平均 O(1)，中间插入删除需要移动元素；LinkedList 基于双向链表，头尾插入删除快，随机访问需要遍历。大多数业务场景优先 ArrayList，只有在频繁头尾操作且数据量大时才考虑 LinkedList。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 集合', '1-3 年', 'Java,HashMap,并发安全', 2, 'HashMap 为什么线程不安全？多线程下可能出现哪些问题？', 'HashMap 没有同步控制，多线程同时 put 可能覆盖数据、丢失更新，扩容时还可能形成错误的链表结构。JDK 8 虽然改为尾插法，仍然不能保证并发安全。并发场景应使用 ConcurrentHashMap，或在外层加锁。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 集合', '1-3 年', 'Java,ConcurrentHashMap,扩容', 3, 'ConcurrentHashMap 在 JDK 8 中是如何扩容的？什么是协助扩容？', 'JDK 8 的 ConcurrentHashMap 使用 CAS 加 synchronized 锁住桶头节点。扩容时通过 ForwardingNode 标记已迁移的桶，其他线程遇到 ForwardingNode 会参与迁移，即协助扩容。这样可以把扩容工作分摊到多个线程，减少单线程停顿。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 集合', '1-3 年', 'Java,CopyOnWriteArrayList,读写分离', 2, 'CopyOnWriteArrayList 的原理是什么？适合什么场景？', '写操作会复制一份新数组，在新数组上修改后再替换引用，读操作始终访问旧快照，因此读写不互相阻塞。适合读多写少、数据量不大的场景，例如监听器列表、配置快照。写操作成本高，数据量大时不适合频繁更新。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 集合', '1-3 年', 'Java,LinkedHashMap,LRU', 2, '如何用 LinkedHashMap 实现一个简单的 LRU 缓存？', '构造 LinkedHashMap 时设置 accessOrder 为 true，并重写 removeEldestEntry，当 size 超过容量时返回 true 删除最久未访问的条目。get 和 put 会把访问的 entry 移到链表尾部，链表头部就是最久未使用的元素。生产环境还要考虑并发和过期策略。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 集合', '1-3 年', 'Java,TreeMap,红黑树', 2, 'TreeMap 的底层结构和排序规则是什么？', 'TreeMap 基于红黑树实现，key 必须可比较或传入 Comparator，查询、插入、删除平均 O(log n)，并且可以按 key 有序遍历。适合需要范围查询、排序输出的场景。key 为 null 时行为取决于是否提供 Comparator。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 并发', '1-3 年', 'Java,线程池,状态转换', 2, '线程池有哪几种状态？状态之间如何转换？', 'ThreadPoolExecutor 有 RUNNING、SHUTDOWN、STOP、TIDYING、TERMINATED 五种状态。shutdown 从 RUNNING 进入 SHUTDOWN，不再接收新任务但处理队列；shutdownNow 进入 STOP，中断工作线程并丢弃队列；任务和线程都结束后进入 TIDYING，最后 TERMINATED。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 并发', '1-3 年', 'Java,线程池,阻塞队列', 2, '线程池的阻塞队列如何选择？ArrayBlockingQueue 和 LinkedBlockingQueue 有什么区别？', 'ArrayBlockingQueue 基于数组，有界，内存连续，适合控制资源；LinkedBlockingQueue 基于链表，默认近似无界，吞吐较高但可能堆积任务。CPU 密集型任务应使用有界队列并配合拒绝策略，避免无限堆积导致内存溢出。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 并发', '1-3 年', 'Java,ForkJoinPool,工作窃取', 3, 'ForkJoinPool 的工作窃取机制是什么？适合什么任务？', 'ForkJoinPool 把大任务拆成小任务放入工作队列，空闲线程从其他线程队列尾部窃取任务执行。适合可以递归拆分、计算密集且任务粒度可控的场景，例如并行归并、批量计算。任务切分过细会增加调度开销，阻塞型任务不适合直接占用计算线程。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 并发', '1-3 年', 'Java,StampedLock,读写锁', 3, 'StampedLock 和 ReentrantReadWriteLock 有什么区别？', 'StampedLock 提供乐观读、悲观读和写锁，乐观读不加锁，通过版本戳校验数据是否被修改，读多写少时性能更好。它不可重入，也不支持条件变量，使用不当容易死锁。ReentrantReadWriteLock 可重入、支持公平锁和条件队列，语义更完整。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 并发', '1-3 年', 'Java,读写锁,并发', 2, '读写锁适合什么场景？为什么读锁不能直接升级为写锁？', '读写锁适合读多写少、读操作可以并发的场景。读锁升级为写锁需要先释放读锁再获取写锁，否则多个读线程互相等待会造成死锁。ReentrantReadWriteLock 支持写锁降级为读锁，但不支持读锁直接升级。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 并发', '1-3 年', 'Java,伪共享,缓存行', 3, '什么是伪共享？如何避免伪共享？', '多个线程修改同一缓存行中的不同变量时，缓存行会在 CPU 核心之间反复失效，导致性能下降，这就是伪共享。可以通过填充字段让变量独占缓存行，或使用 @Contended 注解。高并发计数器、队列等底层结构常用这种优化。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 并发', '1-3 年', 'Java,JMM,happens-before', 2, 'JMM 中的 happens-before 规则有什么作用？', 'happens-before 定义了多线程操作的可见性和有序性约束。如果 A happens-before B，则 A 的结果对 B 可见。常见规则包括程序顺序、监视器锁、volatile 变量、线程启动和 join。它帮助开发者判断是否需要额外同步，而不是保证所有指令绝对不重排。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 并发', '1-3 年', 'Java,线程中断,取消任务', 2, '如何正确停止一个线程？interrupt 和 stop 有什么区别？', '应使用 interrupt 协作式中断，线程通过 isInterrupted 或捕获 InterruptedException 决定如何退出。stop 会强制终止线程并释放锁，可能造成数据不一致，已经废弃。中断只是设置标志，不会直接杀死线程，阻塞方法会抛出 InterruptedException。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 并发', '1-3 年', 'Java,线程池,监控', 2, '线上如何监控线程池？需要关注哪些指标？', '关注活跃线程数、池大小、队列长度、任务完成数、拒绝次数、任务耗时和异常数。可以通过 ThreadPoolExecutor 的 getter、Micrometer 指标或自定义拒绝策略上报。队列持续增长或拒绝率上升通常说明容量不足或下游变慢。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 并发', '1-3 年', 'Java,并发容器,选型', 2, 'ConcurrentHashMap、CopyOnWriteArrayList、BlockingQueue 分别适合什么场景？', 'ConcurrentHashMap 适合高并发键值读写；CopyOnWriteArrayList 适合读多写少且数据量小的列表；BlockingQueue 适合生产者消费者解耦和削峰。选型要看读写比例、数据规模、是否需要阻塞和一致性要求，不能只按容器名称选择。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('JVM', '1-3 年', 'JVM,ZGC,低延迟', 3, 'ZGC 的核心特点是什么？适合什么场景？', 'ZGC 是低延迟垃圾收集器，通过着色指针、读屏障和并发标记整理，把停顿控制在毫秒级，适合大堆、低延迟服务。它更关注停顿而不是吞吐，CPU 和内存开销相对更高。选型要结合堆大小、延迟目标和吞吐要求。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('JVM', '1-3 年', 'JVM,对象晋升,分代', 2, '对象什么时候会晋升到老年代？', '对象在新生代经过多次 Minor GC 仍然存活，年龄达到阈值后会晋升老年代；大对象可能直接进入老年代；Survivor 空间不足时也可能提前晋升。晋升过早会增加 Full GC 风险，需要结合对象生命周期和 GC 日志分析。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('JVM', '1-3 年', 'JVM,调优,参数', 3, 'JVM 调优一般看哪些参数和指标？', '先看堆大小、新生代比例、Survivor 比例、元空间、GC 收集器和停顿目标，再结合 GC 日志看吞吐、停顿、晋升和 Full GC 频率。调优不是背参数，而是先定位瓶颈：内存泄漏、对象分配过快、缓存过大或线程过多，再决定是否调整。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('JVM', '1-3 年', 'JVM,内存泄漏,OOM', 3, '内存泄漏和内存溢出有什么区别？如何排查？', '内存泄漏是对象不再使用却仍被引用，导致内存逐渐耗尽；内存溢出是申请内存超过限制。排查可以用 jmap 导出堆、MAT 或 JProfiler 分析支配树和 GC Roots，结合压测复现。常见原因包括静态集合、ThreadLocal、监听器未注销和缓存无上限。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('JVM', '1-3 年', 'JVM,Class文件,字节码', 2, 'Class 文件的基本结构包含哪些部分？', 'Class 文件包含魔数、版本号、常量池、访问标志、类索引、父类索引、接口表、字段表、方法表、属性表等。JVM 通过常量池和方法表中的字节码指令执行程序。理解 Class 文件有助于分析字节码增强、反射和类加载问题。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('JVM', '1-3 年', 'JVM,G1,Mixed GC', 3, 'G1 的 Region 和 Mixed GC 是怎么工作的？', 'G1 把堆划分为大小相等的 Region，逻辑上仍有新生代和老年代。年轻代回收称为 Young GC，当老年代占用达到阈值后会触发 Mixed GC，同时回收年轻代和部分老年代 Region。G1 通过 Remembered Set 维护跨 Region 引用，并以预测停顿模型选择回收集合。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Spring Boot 和 Spring', '1-3 年', 'Spring,IoC,容器启动', 3, 'Spring IoC 容器的启动流程大致是什么？', '容器启动先加载配置元数据，创建 BeanFactory，执行 BeanFactoryPostProcessor 修改 Bean 定义，再实例化 BeanPostProcessor，随后依次创建非懒加载单例 Bean，执行初始化前后增强，最后发布上下文刷新完成事件。理解流程有助于定位 Bean 初始化顺序和扩展点问题。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Spring Boot 和 Spring', '1-3 年', 'Spring,Bean生命周期', 3, 'Spring Bean 的完整生命周期包含哪些阶段？', '主要阶段包括实例化、属性填充、Aware 回调、BeanPostProcessor 前置处理、初始化方法、BeanPostProcessor 后置处理、使用和销毁。销毁阶段会执行 DisposableBean 和 destroy-method。AOP 代理通常在 BeanPostProcessor 后置处理阶段生成。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Spring Boot 和 Spring', '1-3 年', 'Spring,循环依赖,三级缓存', 3, 'Spring 如何解决单例 Bean 的循环依赖？三级缓存分别存什么？', 'Spring 通过三级缓存解决 setter 或字段注入的单例循环依赖：singletonObjects 存完整 Bean，earlySingletonObjects 存提前暴露的半成品，singletonFactories 存 ObjectFactory。A 实例化后先暴露工厂，B 注入 A 时获取早期引用，从而打破循环。构造器注入无法这样解决。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Spring Boot 和 Spring', '1-3 年', 'Spring,循环依赖,构造器', 2, '构造器注入的循环依赖为什么解决不了？可以怎么处理？', '构造器注入要求在创建对象前就拿到依赖，两个 Bean 互相等待对方实例化，无法提前暴露半成品。可以通过 @Lazy 延迟注入、改为 setter 或字段注入、拆分职责或引入事件/接口解耦。生产代码更推荐从设计上消除循环依赖。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Spring Boot 和 Spring', '1-3 年', 'Spring,AOP,代理', 2, 'Spring AOP 在什么情况下使用 JDK 代理，什么情况下使用 CGLIB？', '目标类实现了接口时默认使用 JDK 动态代理，没有接口时使用 CGLIB；可以通过 proxyTargetClass=true 强制 CGLIB。CGLIB 通过继承生成子类，不能代理 final 类和 final 方法。Spring Boot 2.x 之后默认更倾向于 CGLIB。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Spring Boot 和 Spring', '1-3 年', 'Spring,事务传播,REQUIRES_NEW,NESTED', 3, 'REQUIRES_NEW 和 NESTED 事务传播行为有什么区别？', 'REQUIRES_NEW 会挂起当前事务并开启一个全新事务，内外事务互不影响；NESTED 在当前事务中创建保存点，内层失败可以回滚到保存点，外层仍可提交。NESTED 依赖 JDBC 保存点支持，REQUIRES_NEW 会占用额外连接，使用时要注意连接池和锁。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Spring Boot 和 Spring', '1-3 年', 'Spring,事务失效,@Transactional', 3, '@Transactional 常见的失效场景有哪些？', '常见失效包括同类方法自调用绕过代理、方法不是 public、异常被 catch 吞掉、抛出受检异常但未配置 rollbackFor、传播行为设置不当、对象不是 Spring 管理的 Bean、异步线程丢失事务上下文。排查时先确认代理是否生效，再看异常和事务管理器配置。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Spring Boot 和 Spring', '1-3 年', 'Spring Boot,自动配置,条件装配', 3, 'Spring Boot 自动配置的原理是什么？', '启动类上的 @EnableAutoConfiguration 通过 AutoConfigurationImportSelector 加载自动配置类，结合 @Conditional 系列注解按类路径、Bean 是否存在、配置属性等条件决定是否装配。自动配置类通常配合 @EnableConfigurationProperties 和 Starter 使用。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Spring Boot 和 Spring', '1-3 年', 'Spring Boot,Starter,自定义', 3, '如何自定义一个 Spring Boot Starter？', '通常拆成 autoconfigure 和 starter 两个模块：autoconfigure 放自动配置类、条件注解和配置属性，starter 只负责引入依赖。自动配置类通过 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 注册，并提供可覆盖的默认 Bean。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Spring Boot 和 Spring', '1-3 年', 'Spring MVC,请求流程', 3, 'Spring MVC 处理一个 HTTP 请求的流程是什么？', '请求先到 DispatcherServlet，随后由 HandlerMapping 找到处理器，HandlerAdapter 调用 Controller，Controller 返回 ModelAndView 或响应体，ViewResolver 解析视图或消息转换器写出 JSON，最后执行拦截器后置逻辑并返回响应。异常由 HandlerExceptionResolver 统一处理。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Spring Boot 和 Spring', '1-3 年', 'Spring,拦截器,Filter', 2, 'Spring 拦截器和 Servlet Filter 有什么区别？', 'Filter 属于 Servlet 规范，在请求进入 DispatcherServlet 前后执行，能包装请求和响应；拦截器属于 Spring MVC，在 Handler 调用前后执行，能访问 Handler 和 ModelAndView。权限、日志、跨域等通用逻辑可以用 Filter，业务级鉴权和参数处理更适合拦截器。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Spring Boot 和 Spring', '1-3 年', 'Spring Security,JWT,过滤器链', 3, 'Spring Security 中 JWT 认证过滤器应该放在什么位置？', 'JWT 过滤器通常继承 OncePerRequestFilter，并放在 UsernamePasswordAuthenticationFilter 之前。它解析 Authorization 头、校验签名和过期时间，然后把 Authentication 写入 SecurityContext。还需要配置无状态会话、放行登录和预检请求，并处理 401 和 403。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('MySQL 和 MyBatis', '1-3 年', 'MySQL,B+树,索引', 2, 'MySQL 为什么使用 B+ 树作为索引结构？', 'B+ 树非叶子节点只存键和指针，扇出大、树高低，能减少磁盘 I/O；叶子节点通过链表连接，范围查询和排序效率高；所有查询都要走到叶子节点，路径稳定。B 树非叶子节点也存数据，Hash 索引不支持范围查询和排序。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('MySQL 和 MyBatis', '1-3 年', 'MySQL,聚簇索引,回表', 2, '什么是聚簇索引和回表？如何减少回表？', 'InnoDB 主键索引是聚簇索引，叶子节点直接存整行数据；二级索引叶子节点存主键值，查询非索引字段时需要根据主键再查一次聚簇索引，这就是回表。可以通过覆盖索引、把常用查询字段加入联合索引或减少 SELECT 字段来降低回表。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('MySQL 和 MyBatis', '1-3 年', 'MySQL,覆盖索引,联合索引', 2, '什么是覆盖索引？如何判断一条查询是否使用了覆盖索引？', '覆盖索引指查询需要的字段都能从索引中获取，不需要回表。可以通过 EXPLAIN 的 Extra 字段出现 Using index 判断。设计联合索引时把 SELECT、WHERE、ORDER BY 常用字段放在索引中，但要注意索引宽度和写入成本。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('MySQL 和 MyBatis', '1-3 年', 'MySQL,最左前缀,联合索引', 2, '联合索引的最左前缀原则是什么？范围查询后还能用后面的索引列吗？', '联合索引按定义顺序匹配，查询条件必须从最左列开始连续使用才能充分利用索引。遇到范围查询后，后续列通常无法继续用于索引查找，但可能用于索引条件下推。设计索引时把等值条件和高区分度列放在前面。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('MySQL 和 MyBatis', '1-3 年', 'MySQL,索引失效,EXPLAIN', 2, 'MySQL 索引失效的常见原因有哪些？', '常见原因包括隐式类型转换、在索引列上使用函数或运算、违背最左前缀、OR 条件未全部命中索引、模糊查询以通配符开头、统计信息不准导致优化器放弃索引、索引区分度太低。排查时结合 EXPLAIN 看 type、key、rows 和 Extra。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('MySQL 和 MyBatis', '1-3 年', 'MySQL,MVCC,ReadView', 3, 'MVCC 的 ReadView 包含哪些内容？可见性如何判断？', 'ReadView 包含活跃事务 ID 集合 m_ids、最小活跃事务 ID、下一个事务 ID 和创建者事务 ID。判断版本可见性时，比较行版本的事务 ID 与 ReadView 边界：小于最小活跃事务 ID 可见，大于等于下一个事务 ID 不可见，位于中间则看是否在活跃集合中。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('MySQL 和 MyBatis', '1-3 年', 'MySQL,幻读,Next-Key Lock', 3, 'InnoDB 在可重复读隔离级别下如何解决幻读？', 'InnoDB 通过 MVCC 解决快照读的幻读，通过 Next-Key Lock 解决当前读的幻读。Next-Key Lock 是记录锁和间隙锁的组合，锁住索引记录及间隙，阻止其他事务在范围内插入。不同索引和查询条件会影响加锁范围，需要通过锁等待分析验证。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('MySQL 和 MyBatis', '1-3 年', 'MySQL,redo log,undo log,binlog', 3, 'redo log、undo log 和 binlog 分别解决什么问题？', 'redo log 保证崩溃恢复和持久性，采用 WAL 先写日志；undo log 支持事务回滚和 MVCC 版本链；binlog 记录逻辑变更，用于主从复制和数据恢复。三者职责不同，生产环境还要理解两阶段提交如何保证 redo 和 binlog 一致。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('MySQL 和 MyBatis', '1-3 年', 'MySQL,死锁,排查', 3, 'MySQL 死锁如何排查和避免？', '通过 SHOW ENGINE INNODB STATUS 查看最近死锁、等待事务和 SQL，结合 performance_schema 和慢日志定位。避免死锁要统一加锁顺序、缩短事务、减少范围锁、为条件列建立合适索引，并在业务层处理死锁重试。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('MySQL 和 MyBatis', '1-3 年', 'MySQL,慢SQL,优化', 3, '线上慢 SQL 的排查和优化步骤是什么？', '先通过慢查询日志和监控找到具体 SQL，再用 EXPLAIN 分析执行计划、索引使用、扫描行数和排序临时表。优化方向包括补合适索引、改写 SQL、减少回表、拆分大查询、分页优化和归档历史数据。修改后要压测并观察数据库指标。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Redis 和缓存', '1-3 年', 'Redis,缓存穿透,布隆过滤器', 2, '什么是缓存穿透？如何解决？', '缓存穿透是查询大量不存在的数据，缓存不命中后每次都打到数据库。可以通过缓存空值并设置较短过期时间、布隆过滤器提前拦截明显不存在的 key、接口参数校验和限流来解决。恶意攻击场景要结合风控和网关限流。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Redis 和缓存', '1-3 年', 'Redis,缓存击穿,互斥锁', 2, '什么是缓存击穿？互斥锁方案怎么实现？', '缓存击穿是热点 key 过期瞬间大量请求同时查数据库。互斥锁方案是在缓存失效时只允许一个线程获取 Redis 锁并回源，其他线程等待后重试，同时做双重检查。要注意锁超时、锁误删和热点 key 预热，避免锁竞争拖慢整体响应。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Redis 和缓存', '1-3 年', 'Redis,缓存雪崩,过期时间', 2, '什么是缓存雪崩？如何避免？', '缓存雪崩是大量 key 同时过期或 Redis 故障，导致请求集中打到数据库。可以通过过期时间加随机值、多级缓存、Redis 主从哨兵或集群、缓存预热、限流熔断和数据库连接池保护来降低风险。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Redis 和缓存', '1-3 年', 'Redis,分布式锁,Lua', 3, '如何用 Redis 实现一个可靠的分布式锁？', '使用 SET key value NX EX 加锁，value 使用唯一请求标识；释放时用 Lua 脚本先比较 value 再删除，保证原子性。还需要考虑锁过期、业务超时、自动续期和主从切换丢锁。生产环境通常使用 Redisson，而不是手写简单 SETNX。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Redis 和缓存', '1-3 年', 'Redis,Redisson,WatchDog', 3, 'Redisson 的看门狗机制是什么？它解决什么问题？', 'Redisson 加锁默认设置过期时间，同时启动看门狗定时任务，在业务未结束时定期续期，避免业务执行时间超过锁过期时间导致锁被其他线程获取。看门狗依赖客户端存活，进程崩溃后锁最终会过期释放。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Redis 和缓存', '1-3 年', 'Redis,缓存一致性,延迟双删', 3, '缓存和数据库如何保证一致性？为什么通常删除缓存而不是更新缓存？', '常用方案是先更新数据库再删除缓存，并发场景配合延迟双删；强一致场景可以用分布式锁或订阅 binlog 异步刷新。删除缓存更简单，能避免并发更新导致旧值覆盖新值。无论哪种方案都要考虑删除失败、重试和最终一致。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Redis 和缓存', '1-3 年', 'Redis,RDB,AOF,持久化', 2, 'RDB 和 AOF 有什么区别？如何选择？', 'RDB 是快照，文件紧凑、恢复快，但可能丢失最后一次快照后的数据；AOF 记录写命令，数据更完整，但文件更大、恢复更慢。生产环境常用混合持久化，兼顾恢复速度和数据安全性。还要结合业务可接受的数据丢失窗口选择同步策略。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Redis 和缓存', '1-3 年', 'Redis,过期删除,内存淘汰', 2, 'Redis 的过期删除策略和内存淘汰策略有什么区别？', '过期删除针对设置了 TTL 的 key，采用惰性删除和定期删除；内存淘汰是内存达到 maxmemory 后按策略淘汰 key，例如 LRU、LFU、TTL 或随机。两者作用对象和触发条件不同，缓存场景常用 allkeys-lru 或 allkeys-lfu。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Redis 和缓存', '1-3 年', 'Redis,BigKey,热Key', 3, '什么是 BigKey 和热 Key？如何治理？', 'BigKey 是 value 过大或集合元素过多的 key，可能造成网络阻塞、删除卡顿和内存不均；热 Key 是访问量高度集中的 key，可能压垮单个节点。治理包括拆分 key、压缩 value、异步删除、本地缓存、热点探测和集群分片。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Redis 和缓存', '1-3 年', 'Redis,单线程,IO多路复用', 2, 'Redis 单线程为什么还这么快？', 'Redis 主要操作在内存中完成，数据结构高效；使用 IO 多路复用处理大量连接；核心命令执行是单线程，避免锁竞争和线程切换；协议简单、事件驱动。Redis 6 之后网络 IO 可以多线程，但命令执行仍然保持单线程语义。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('微服务与消息队列', '1-3 年', '微服务,服务拆分,边界', 3, '微服务拆分应该遵循哪些原则？', '按业务能力或领域边界拆分，保证高内聚低耦合；每个服务有清晰的数据所有权和接口契约；避免过度拆分导致分布式事务和调用链复杂。还要考虑团队规模、部署独立性、数据一致性和故障隔离能力。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('微服务与消息队列', '1-3 年', 'Nacos,注册中心,服务发现', 2, 'Nacos 的服务注册与发现流程是怎样的？', '服务启动时向 Nacos 注册实例并定期发送心跳，消费者从注册中心获取服务实例列表并本地缓存，通过负载均衡选择实例调用。Nacos 支持临时实例和持久实例，临时实例依赖心跳健康检查，持久实例依赖服务端主动探测。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('微服务与消息队列', '1-3 年', 'OpenFeign,超时,重试', 3, 'OpenFeign 的超时和重试如何配置？重试会带来什么问题？', '可以配置连接超时、读取超时和重试次数，并结合负载均衡选择实例。重试可能放大下游压力，非幂等接口重试还会造成重复扣款或重复下单。生产环境要区分幂等和非幂等接口，设置退避策略，并配合熔断和限流。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('微服务与消息队列', '1-3 年', 'Gateway,限流,网关', 2, 'Spring Cloud Gateway 的核心能力有哪些？如何做限流？', 'Gateway 基于路由、断言和过滤器处理请求，支持动态路由、鉴权、跨域、限流、熔断和日志。限流可以基于 Redis 令牌桶或网关自带过滤器，按 IP、用户、接口维度配置。要注意限流阈值、突发流量和降级策略。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('微服务与消息队列', '1-3 年', 'Sentinel,熔断,降级', 3, 'Sentinel 的熔断降级原理是什么？', 'Sentinel 通过统计资源调用的 QPS、异常比例、慢调用比例等指标，达到阈值后触发熔断，后续请求快速失败或走降级逻辑。熔断有打开、半开和关闭状态，半开时放少量请求探测下游是否恢复。配置要结合业务容忍度和恢复时间。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('微服务与消息队列', '1-3 年', '分布式事务,TCC,Saga,本地消息表', 3, '分布式事务有哪些常见方案？各自适合什么场景？', '常见方案包括 2PC、TCC、Saga、本地消息表和事务消息。2PC 强一致但阻塞和协调者风险高；TCC 业务侵入大但灵活；Saga 适合长流程和可补偿业务；本地消息表适合最终一致。选型要看一致性要求、业务补偿能力和吞吐。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('微服务与消息队列', '1-3 年', 'Kafka,幂等,顺序消息', 3, 'Kafka 如何保证消息顺序和消费幂等？', 'Kafka 只保证同一分区内有序，生产端按业务 key 分区、单分区单消费者可以保证顺序。消费幂等需要业务自己实现，例如唯一键、去重表、版本号或幂等 token。重试和 Rebalance 都可能导致重复消费，不能只依赖消费者组。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('微服务与消息队列', '1-3 年', '消息队列,消息丢失,重复消费', 3, '消息队列如何防止消息丢失和重复消费？', '防丢失要覆盖生产、存储和消费三段：生产端确认、Broker 持久化和副本、消费端手动确认。防重复消费需要业务幂等，例如唯一约束、去重表或状态机。还要处理消费失败重试、死信队列和积压告警。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('微服务与消息队列', '1-3 年', '分布式ID,雪花算法', 2, '分布式 ID 有哪些生成方案？雪花算法有什么优缺点？', '常见方案包括 UUID、数据库自增、号段模式、Redis 自增和雪花算法。雪花算法生成 64 位 ID，包含时间戳、机器 ID 和序列号，趋势递增、性能高；缺点是依赖时钟，时钟回拨可能导致重复，需要处理机器 ID 分配和时钟同步。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('微服务与消息队列', '1-3 年', '链路追踪,TraceId,可观测性', 2, '微服务链路追踪的核心原理是什么？', '请求进入系统时生成全局 TraceId，跨服务调用时通过 HTTP Header 或消息属性传递，每个服务记录 Span 和父子关系，最后在追踪系统中还原调用链。核心价值是定位慢调用、异常服务和依赖瓶颈。还要结合日志、指标和告警形成可观测体系。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('项目场景', '1-3 年', '项目,幂等,接口设计', 2, '如何设计一个幂等的写接口？', '可以从请求唯一标识、数据库唯一约束、状态机条件更新、去重表和分布式锁几个层面设计。前端生成幂等 token，服务端在处理前校验并落库，重复请求直接返回第一次结果。要区分天然幂等和业务幂等，并考虑并发和失败重试。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('项目场景', '1-3 年', '项目,秒杀,库存扣减', 3, '秒杀场景如何防止超卖？', '可以用 Redis 预扣库存、Lua 脚本保证原子性，数据库最终扣减时加条件更新和唯一约束，消息队列异步削峰。还要做限流、防刷、幂等、库存回补和热点隔离。核心是让库存判断和扣减在同一个原子操作中完成。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('项目场景', '1-3 年', '项目,CPU飙高,排查', 3, '线上 CPU 飙高如何排查？', '先用 top 找到高 CPU 进程，再用 top -Hp 或 ps 找到高 CPU 线程，把线程 ID 转成十六进制后到 jstack 中查堆栈，判断是死循环、频繁 GC、锁竞争还是计算密集任务。结合 GC 日志、火焰图和监控确认根因，再决定扩容、限流或改代码。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('项目场景', '1-3 年', '项目,慢请求,排查', 3, '线上接口变慢，你会如何排查？', '先看监控和日志确认是单接口还是全局变慢，再拆分网关、应用、数据库、缓存和下游依赖耗时。应用侧看线程池、GC、锁和连接池，数据库看慢 SQL 和锁等待，下游看超时和重试。定位后用限流、缓存、异步化或扩容止血，再做根因修复。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('项目场景', '1-3 年', 'MySQL,深分页,大表', 2, '大表分页查询越来越慢，如何优化？', '深分页的 offset 很大时数据库需要扫描并丢弃大量行。可以用基于游标的分页，例如 WHERE id > lastId ORDER BY id LIMIT n；或者先查主键再回表。还可以增加覆盖索引、限制最大页数、归档历史数据，避免一次查询过深。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('项目场景', '1-3 年', '项目,灰度发布,回滚', 3, '如何设计灰度发布和快速回滚？', '按用户、流量比例或机器维度切流，灰度环境使用独立配置和监控，观察错误率、延迟和业务指标后逐步放量。发布要保证数据库变更向后兼容，准备一键回滚和开关降级。回滚前要确认数据迁移是否可逆。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java + AI', '1-3 年', 'Spring AI,大模型,框架', 2, 'Spring AI 的核心设计是什么？它解决了什么问题？', 'Spring AI 提供统一的模型调用 API、ChatClient、Advisor、Prompt 模板、结构化输出、工具调用和 RAG 支持，通过适配器屏蔽不同模型厂商差异。它让 Java 团队用熟悉的 Spring 方式接入大模型，同时保留可观测、重试和配置管理能力。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java + AI', '1-3 年', 'Spring AI,ChatClient,Advisor', 3, 'Spring AI 的 ChatClient 和 Advisor 分别负责什么？', 'ChatClient 提供链式调用、流式响应、系统提示词和工具调用入口；Advisor 类似拦截器链，可以在请求前后统一处理对话记忆、RAG 检索、日志、内容安全和上下文压缩。把横切逻辑放进 Advisor，可以避免每个业务方法重复拼装。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java + AI', '1-3 年', 'RAG,检索增强,流程', 2, '标准 RAG 的完整流程是什么？', '离线阶段做文档解析、清洗、切分、Embedding 并写入向量库；在线阶段对用户问题做改写和 Embedding，检索相关片段，经过重排和上下文组装后注入 Prompt，再调用大模型生成答案。还要记录引用来源并做效果评估。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java + AI', '1-3 年', 'RAG,文档切分,Chunk', 3, 'RAG 文档切分有哪些策略？Chunk 大小如何确定？', '常见策略包括固定长度、按段落、按标题层级、语义切分和父子块。Chunk 太小会丢失上下文，太大会引入噪声并增加 Token 成本。要结合文档结构、Embedding 模型窗口和问答粒度做实验，并用召回率和答案质量评估。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java + AI', '1-3 年', 'RAG,Embedding,向量数据库', 3, 'Embedding 和向量数据库在 RAG 中起什么作用？如何选型？', 'Embedding 把文本映射为语义向量，向量数据库负责相似度检索和元数据过滤。选型要看数据规模、延迟、过滤能力、持久化、多租户和运维成本。常见选择包括 pgvector、Milvus、Elasticsearch 向量检索和 Redis 向量能力。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java + AI', '1-3 年', 'RAG,混合检索,重排', 3, '什么是混合检索和重排？为什么需要它们？', '混合检索同时使用向量语义检索和关键词检索，兼顾语义相似和精确匹配；重排模型对初筛结果重新打分，把最相关的片段放到前面。它们可以缓解单一向量检索召回不准、专业术语匹配差和长尾问题。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java + AI', '1-3 年', 'RAG,评估,召回率', 3, 'RAG 系统如何评估？核心指标有哪些？', '离线可以构造问题、标准答案和相关文档集，评估召回率、准确率、MRR、NDCG 和答案忠实度；在线看点击、采纳率、追问率、人工反馈和任务完成率。还要做回归测试，防止知识库更新或 Prompt 调整导致效果回退。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java + AI', '1-3 年', 'RAG,幻觉,引用', 3, 'RAG 如何减少幻觉？如何标注引用来源？', '通过提高检索质量、限制模型只基于给定上下文回答、要求输出引用编号、增加相似度阈值和拒答策略来减少幻觉。生成后可以做引用校验，检查答案中的事实是否能在检索片段中找到。无法回答时要明确说不知道，而不是编造。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java + AI', '1-3 年', 'Agent,ReAct,工具调用', 3, 'Agent 的 ReAct 模式是什么？和普通 ChatBot 有什么区别？', 'ReAct 让模型交替进行推理和行动：先分析问题，选择工具并观察结果，再继续推理，直到给出最终答案。普通 ChatBot 通常只生成文本，Agent 会循环调用工具并根据结果调整计划。生产环境要设置步数、Token 和时间上限。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java + AI', '1-3 年', 'Agent,工具调用,Function Calling', 3, '如何设计一个可靠的 Tool Calling 工具？', '工具描述要清晰、参数用结构化 Schema、所有属性必填并限制类型；执行要有超时、重试、幂等键和结果截断；返回值要简洁，避免把大对象直接塞回上下文。还要限制工具白名单和调用次数，防止模型误调用或无限循环。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java + AI', '1-3 年', 'Agent,状态机,熔断', 3, '多步 Agent 如何避免死循环和上下文爆炸？', '把状态外置并持久化，每一步记录计划、工具结果和下一步；设置最大步数、Token 预算和时间熔断；对重复工具调用做检测，必要时强制收敛或转人工；上下文按重要性分层并做摘要压缩。高风险操作要增加人工确认。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java + AI', '1-3 年', '对话记忆,ChatMemory,上下文', 3, 'AI 应用的对话记忆如何设计？上下文太长怎么办？', '短期记忆保存最近几轮对话，长期记忆抽取用户偏好和事实存入数据库或向量库；上下文超长时做摘要、按相关性检索、保留系统约束和最近消息。还要考虑多租户隔离、隐私脱敏和记忆过期策略。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java + AI', '1-3 年', 'Prompt注入,安全,防护', 3, '什么是 Prompt 注入？如何防护？', 'Prompt 注入是用户输入或外部文档中包含恶意指令，诱导模型忽略系统规则。防护包括把外部内容标记为数据、系统提示词明确优先级、输入输出过滤、工具权限最小化、敏感操作人工确认和隔离检索内容。不能只依赖模型自觉。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java + AI', '1-3 年', '结构化输出,JSON Schema,解析', 2, '如何让大模型稳定输出结构化 JSON？', '优先使用模型厂商支持的 JSON 模式或 JSON Schema，明确字段、类型和枚举，降低温度并要求只输出 JSON。服务端仍要做容错解析、字段校验和失败重试，不能假设模型永远合规。复杂结构可以拆成多步生成。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java + AI', '1-3 年', '流式输出,SSE,用户体验', 2, '大模型流式输出如何实现？SSE 和 WebSocket 怎么选？', '流式输出通过 SSE、WebSocket 或 HTTP chunked 把增量 Token 推给前端。SSE 单向、实现简单、适合文本生成；WebSocket 双向、适合语音或复杂交互。后端要处理连接超时、断线重连、首包延迟和取消生成。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java + AI', '1-3 年', 'Token,成本优化,缓存', 3, 'AI 应用如何优化 Token 成本和延迟？', '控制上下文长度、做 Prompt 压缩和摘要、缓存高频问答、按任务选择小模型、批量请求和流式返回。RAG 只注入相关片段，避免整篇文档。还要监控 Token 消耗、首 Token 延迟和单次成本，设置预算和降级策略。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java + AI', '1-3 年', '多模型,路由,降级', 3, '多模型接入时如何做路由和降级？', '按任务类型、成本、延迟和可用性选择模型，建立统一抽象层屏蔽厂商差异；设置主备模型和超时降级，失败时切换备用或返回缓存结果。路由要可配置、可观测，并记录每次调用的模型、Token、延迟和错误。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java + AI', '1-3 年', '可观测性,AI,监控', 3, 'AI 应用需要监控哪些指标？', '监控请求量、成功率、首 Token 延迟、总延迟、Token 消耗、成本、工具调用成功率、RAG 召回质量、用户反馈和降级次数。还要记录 Prompt 版本、模型版本和检索片段，方便定位效果回退。日志要注意脱敏和隐私。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 全栈', '1-3 年', '全栈,接口契约,前后端协作', 2, '前后端接口契约应该包含哪些内容？如何减少联调成本？', '契约至少包括 URL、方法、请求参数、响应结构、错误码、分页和鉴权方式。使用 OpenAPI 或 Swagger 维护文档，统一响应体和错误码，前后端基于契约并行开发。变更要版本化并通知对方，联调前先做 Mock。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 全栈', '1-3 年', '全栈,跨域,CORS,预检', 2, '前后端分离时跨域是怎么产生的？开发和生产环境分别怎么处理？', '浏览器同源策略要求协议、域名和端口一致，否则跨域。开发环境可用 Vite 代理，生产环境由后端配置 CORS 或 Nginx 反向代理。带 Cookie 的请求需要 allowCredentials，并明确指定 Origin；Spring Security 要放行 OPTIONS 预检请求。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 全栈', '1-3 年', '全栈,JWT,登录态', 3, 'JWT 应该存在 localStorage、sessionStorage 还是 Cookie？', 'localStorage 方便但易受 XSS 读取；sessionStorage 生命周期短但同样受 XSS 影响；HttpOnly、Secure、SameSite Cookie 能降低 XSS 窃取风险，但要处理 CSRF 和跨域。生产系统常用短 Access Token 加 HttpOnly Refresh Token，并配合 CSP 和 CSRF 防护。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 全栈', '1-3 年', '全栈,JWT,刷新令牌', 3, 'JWT 如何续期？刷新令牌需要注意什么？', 'Access Token 设置较短有效期，Refresh Token 设置较长有效期并存储服务端可撤销状态。刷新时校验 Refresh Token、轮换新令牌并让旧令牌失效。要防止刷新令牌泄露、重放和并发刷新，支持主动登出和异常设备下线。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 全栈', '1-3 年', '全栈,联调,排错', 2, '前后端联调出现 401、403、CORS 错误时如何排查？', '先看浏览器 Network 的请求头、响应码和响应体，确认是否携带 Authorization 和正确格式；401 检查登录态和 Token 过期，403 检查权限和 Security 配置，CORS 检查 Origin、预检请求和 allowCredentials。再用后端日志和接口工具复现，区分前端、网关和后端问题。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Java 全栈', '1-3 年', '全栈,Nginx,部署,反向代理', 3, '前端构建产物如何部署？Nginx 反向代理要注意什么？', '前端构建后把静态文件放到 Nginx 目录，配置 history 路由回退到 index.html；/api 反向代理到后端，传递 Host、X-Forwarded-For 和 X-Forwarded-Proto。要配置 gzip、缓存、HTTPS、超时和跨域策略，后端要正确识别代理后的客户端地址。', '网络整理-2026-10-28', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT INTO track_question (track_id, question_id, sort_order, weight, enabled, created_at, updated_at)
SELECT
    t.id,
    q.id,
    0,
    110,
    1,
    CURRENT_TIMESTAMP(3),
    CURRENT_TIMESTAMP(3)
FROM question_bank q
JOIN interview_track t
  ON t.code IN ('java-backend-campus', 'java-backend-junior')
WHERE q.source = '网络整理-2026-10-28'
  AND q.status = 'ACTIVE'
  AND q.deleted = 0
  AND q.category IN (
      'Java 基础', 'Java 集合', 'Java 并发', 'JVM',
      'Spring Boot 和 Spring', 'MySQL 和 MyBatis', 'Redis 和缓存',
      '微服务与消息队列', '项目场景'
  );

INSERT INTO track_question (track_id, question_id, sort_order, weight, enabled, created_at, updated_at)
SELECT
    t.id,
    q.id,
    0,
    110,
    1,
    CURRENT_TIMESTAMP(3),
    CURRENT_TIMESTAMP(3)
FROM question_bank q
JOIN interview_track t ON t.code = 'java-fullstack-junior'
WHERE q.source = '网络整理-2026-10-28'
  AND q.status = 'ACTIVE'
  AND q.deleted = 0
  AND q.category <> 'Java + AI';

INSERT INTO track_question (track_id, question_id, sort_order, weight, enabled, created_at, updated_at)
SELECT
    t.id,
    q.id,
    0,
    110,
    1,
    CURRENT_TIMESTAMP(3),
    CURRENT_TIMESTAMP(3)
FROM question_bank q
JOIN interview_track t ON t.code = 'java-ai'
WHERE q.source = '网络整理-2026-10-28'
  AND q.status = 'ACTIVE'
  AND q.deleted = 0
  AND q.category <> 'Java 全栈';

INSERT INTO track_question (track_id, question_id, sort_order, weight, enabled, created_at, updated_at)
SELECT
    t.id,
    q.id,
    0,
    110,
    1,
    CURRENT_TIMESTAMP(3),
    CURRENT_TIMESTAMP(3)
FROM question_bank q
JOIN interview_track t ON t.code = 'java-microservices'
WHERE q.source = '网络整理-2026-10-28'
  AND q.status = 'ACTIVE'
  AND q.deleted = 0
  AND q.category IN (
      'Java 基础', 'Java 并发', 'Spring Boot 和 Spring',
      'MySQL 和 MyBatis', 'Redis 和缓存', '微服务与消息队列', '项目场景'
  );
