INSERT INTO question_bank
(category, level, tags, difficulty, question, reference_answer, source, status, created_at, updated_at)
VALUES
('JavaScript', '校招', 'JavaScript,事件循环,微任务', 2, 'JavaScript 的事件循环机制是什么？微任务和宏任务的执行顺序是怎样的？', '同步代码先在调用栈执行，调用栈清空后先清空微任务队列，再取一个宏任务执行，如此循环。Promise.then、queueMicrotask 属于微任务，setTimeout、setInterval、I/O、UI 渲染属于宏任务。经典顺序是同步代码、微任务、宏任务。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('JavaScript', '校招', 'JavaScript,闭包,作用域', 2, '什么是闭包？闭包有哪些使用场景和风险？', '闭包是函数和其词法环境的组合，内部函数可以访问外部函数的变量，即使外部函数已经执行完。常见场景有数据封装、防抖节流、事件回调和柯里化。闭包会延长变量生命周期，使用不当可能造成内存泄漏。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('JavaScript', '校招', 'JavaScript,原型链,继承', 2, '原型和原型链是什么？instanceof 的原理是什么？', '每个对象都有内部原型指向另一个对象，逐级向上形成原型链，直到 Object.prototype，其原型为 null。访问属性时先查自身再沿原型链查找。instanceof 检查构造函数的 prototype 是否出现在对象的原型链上。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('JavaScript', '1-3 年', 'JavaScript,this,绑定规则', 2, 'JavaScript 中 this 的绑定规则有哪些？箭头函数有什么不同？', 'this 的绑定优先级大致是 new 绑定、显式 bind/call/apply、隐式对象调用、默认绑定。严格模式下默认绑定是 undefined，非严格模式指向全局对象。箭头函数没有自己的 this，捕获定义时外层作用域的 this，也不能被 call/apply 修改。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('JavaScript', '1-3 年', 'JavaScript,Promise,并发控制', 3, 'Promise.all、allSettled、any、race 有什么区别？', 'all 等待全部成功，任一失败立即 reject；allSettled 等待全部结束并返回每个结果的状态；any 返回第一个成功的 Promise，全部失败才 reject；race 返回第一个结束的 Promise，无论成功失败。并发请求要根据是否需要全量结果和失败策略选择。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('JavaScript', '1-3 年', 'JavaScript,async,await,异步', 2, 'async/await 的原理是什么？它和 Promise 有什么关系？', 'async 函数返回 Promise，await 会暂停当前函数的执行，等待 Promise 落定后继续，本质是 Generator 加 Promise 的语法糖。await 后面的代码相当于放进 then 回调，因此会进入微任务队列。错误处理可以用 try/catch。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('JavaScript', '1-3 年', 'JavaScript,模块化,ESModule,CommonJS', 2, 'ES Module 和 CommonJS 有什么区别？', 'ES Module 是静态模块系统，编译时确定依赖，支持 tree shaking 和异步加载；CommonJS 是运行时加载，使用 require 和 module.exports，适合 Node 传统模块。ES Module 导出的是绑定引用，CommonJS 导出的是值的拷贝。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('JavaScript', '校招', 'JavaScript,事件,委托', 2, '事件冒泡、事件捕获和事件委托分别是什么？', '事件捕获从最外层向目标元素传播，事件冒泡从目标元素向外层传播。事件委托利用冒泡，把监听器绑定在父元素上，通过 event.target 判断具体子元素。它减少监听器数量，适合动态列表，但不适合不冒泡的事件。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('JavaScript', '校招', 'JavaScript,深拷贝,浅拷贝', 2, '深拷贝和浅拷贝有什么区别？如何实现深拷贝？', '浅拷贝只复制第一层引用，嵌套对象仍然共享；深拷贝递归复制所有层级。常见实现有 JSON 序列化、structuredClone 和递归拷贝。JSON 方案会丢失函数、undefined、Date 和循环引用，生产环境要按数据类型选择。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('JavaScript', '1-3 年', 'JavaScript,防抖,节流', 2, '防抖和节流有什么区别？分别适合什么场景？', '防抖是在最后一次触发后延迟执行，适合搜索输入、窗口调整等高频事件；节流是固定时间间隔最多执行一次，适合滚动、拖拽和频繁点击。两者都能降低回调频率，但防抖会重置计时，节流不会。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('TypeScript', '1-3 年', 'TypeScript,interface,type', 2, 'TypeScript 中 interface 和 type 有什么区别？', 'interface 主要用于描述对象结构，支持声明合并和 extends；type 可以描述联合类型、交叉类型、元组和映射类型，更灵活但不能声明合并。对外暴露的对象模型常用 interface，复杂类型组合常用 type。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('TypeScript', '1-3 年', 'TypeScript,泛型,约束', 2, 'TypeScript 泛型有什么作用？如何给泛型加约束？', '泛型让类型在定义时保持开放，在使用时确定，提升复用和类型安全。可以用 extends 约束泛型必须满足某个结构，用 keyof 约束属性名，用默认类型提供兜底。泛型不应滥用，简单场景直接写具体类型更清晰。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('TypeScript', '1-3 年', 'TypeScript,类型收窄,联合类型', 3, 'TypeScript 的类型收窄有哪些常见方式？', '常见方式包括 typeof、instanceof、in、字面量判断、可辨识联合和类型谓词 is。收窄让联合类型在分支中变成更具体的类型，从而安全访问属性。自定义类型谓词要保证运行时判断和类型声明一致。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('TypeScript', '1-3 年', 'TypeScript,工具类型,映射类型', 2, 'Pick、Omit、Partial、Record 这些工具类型分别有什么用？', 'Pick 选择部分属性，Omit 排除部分属性，Partial 把所有属性变为可选，Record 构造键值映射类型。它们基于映射类型实现，适合从已有类型派生 DTO、表单和配置结构。使用时要注意可选属性对运行时的影响。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('CSS', '校招', 'CSS,盒模型,box-sizing', 1, '标准盒模型和 IE 盒模型有什么区别？box-sizing 有什么作用？', '标准盒模型的 width 只包含内容区，padding 和 border 额外增加总宽度；IE 盒模型的 width 包含内容、padding 和 border。box-sizing: border-box 让元素宽度包含内边距和边框，布局时更直观，现代项目通常全局设置。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('CSS', '1-3 年', 'CSS,BFC,布局', 2, '什么是 BFC？BFC 可以解决哪些问题？', 'BFC 是块级格式化上下文，内部布局与外部隔离。触发方式包括 overflow 非 visible、display flow-root、float、position absolute/fixed 等。BFC 可以清除浮动、防止 margin 合并、避免父元素高度塌陷，并隔离内外布局。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('CSS', '校招', 'CSS,Flex,Grid', 2, 'Flex 和 Grid 有什么区别？分别适合什么布局？', 'Flex 是一维布局，适合一行或一列内的对齐、分配空间和排序；Grid 是二维布局，适合行列同时控制的页面骨架和复杂卡片。实际项目常组合使用，外层 Grid 做页面结构，内部 Flex 做组件对齐。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('CSS', '校招', 'CSS,position,定位', 2, 'CSS 中 static、relative、absolute、fixed、sticky 有什么区别？', 'static 是默认文档流；relative 相对自身原位置偏移且保留占位；absolute 相对最近的非 static 祖先定位并脱离文档流；fixed 相对视口定位；sticky 在滚动到阈值后吸附。absolute 要注意定位上下文，sticky 受父容器滚动范围限制。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('CSS', '1-3 年', 'CSS,层叠上下文,z-index', 3, '什么是层叠上下文？z-index 为什么有时不生效？', '层叠上下文决定元素在 Z 轴的绘制顺序，由 position 加 z-index、transform、opacity、filter 等属性创建。子元素的 z-index 只在所属层叠上下文内比较。如果父元素创建了新的层叠上下文，子元素再大的 z-index 也无法越过父级层级。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('CSS', '校招', 'CSS,响应式,媒体查询', 2, '如何实现响应式布局？媒体查询和容器查询有什么区别？', '响应式布局可以用流式布局、Flex/Grid、相对单位和媒体查询。媒体查询根据视口宽度切换样式；容器查询根据父容器尺寸切换，更适合可复用组件。移动端还要处理视口、触摸目标和图片自适应。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('浏览器与网络', '1-3 年', '浏览器,HTTP缓存,强缓存,协商缓存', 3, '强缓存和协商缓存有什么区别？实际项目如何配置？', '强缓存由 Cache-Control 或 Expires 控制，命中时不请求服务器；协商缓存通过 ETag 或 Last-Modified 校验，未修改返回 304。静态资源通常设置较长强缓存并带内容哈希，HTML 使用协商缓存或不缓存，接口按业务敏感性配置。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('浏览器与网络', '1-3 年', '浏览器,CORS,预检请求', 3, 'CORS 预检请求什么时候触发？如何减少预检开销？', '非简单请求会先发 OPTIONS 预检，例如使用 PUT/DELETE、Content-Type 为 application/json 或带 Authorization 自定义头。可以通过 Access-Control-Max-Age 缓存预检结果、同域反向代理或把请求设计成简单请求来减少开销。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('浏览器与网络', '校招', '浏览器,存储,Cookie,localStorage', 2, 'Cookie、localStorage、sessionStorage 和 IndexedDB 有什么区别？', 'Cookie 容量小、会随请求发送，可设置 HttpOnly 和 SameSite；localStorage 持久化、容量较大但同步阻塞；sessionStorage 仅在当前标签页会话有效；IndexedDB 支持大容量异步存储和复杂查询。登录令牌和敏感数据要结合安全属性选择存储方式。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('浏览器与网络', '1-3 年', '浏览器,渲染流程,关键渲染路径', 3, '浏览器从 HTML 到页面渲染的流程是什么？', '大致流程是解析 HTML 构建 DOM，解析 CSS 构建 CSSOM，合并为渲染树，进行布局计算，绘制图层，最后合成上屏。JavaScript 可能阻塞解析，CSS 可能阻塞渲染。优化要减少关键资源、避免强制同步布局和使用合适的加载策略。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('浏览器与网络', '1-3 年', '浏览器,重排,重绘', 2, '重排和重绘有什么区别？如何减少重排？', '重排是布局几何信息变化，浏览器需要重新计算位置和大小；重绘是外观变化但布局不变。重排成本更高。减少重排可以批量修改 DOM、使用 transform 和 opacity 做动画、避免频繁读取布局属性、使用文档片段和虚拟列表。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('浏览器与网络', '1-3 年', 'HTTP2,多路复用,网络', 2, 'HTTP/2 相比 HTTP/1.1 有哪些改进？', 'HTTP/2 支持二进制分帧、多路复用、头部压缩和服务器推送，减少连接数、队头阻塞和重复头部开销。它仍然基于 TCP，丢包时可能影响所有流；HTTP/3 基于 QUIC 进一步优化。静态资源部署要结合协议版本和 CDN 能力。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('浏览器与网络', '1-3 年', 'HTTPS,TLS,加密', 2, 'HTTPS 的握手过程解决了哪些问题？', 'HTTPS 通过 TLS 握手协商密钥、验证服务器证书并建立加密通道，解决窃听、篡改和身份冒充。握手结合非对称加密交换密钥、对称加密传输数据、证书链验证身份。前端要注意混合内容、证书过期和 HSTS。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('浏览器与网络', '1-3 年', 'WebSocket,SSE,实时通信', 2, 'WebSocket 和 SSE 有什么区别？分别适合什么场景？', 'WebSocket 是全双工长连接，客户端和服务端都能主动发送消息，适合聊天、协作和实时游戏；SSE 是服务器到客户端的单向流，基于 HTTP，自动重连，适合通知、日志和 AI 流式输出。选型要看是否需要双向通信和基础设施支持。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Vue', '1-3 年', 'Vue3,响应式,Proxy', 3, 'Vue3 的响应式系统相比 Vue2 有哪些改进？', 'Vue3 使用 Proxy 和 Reflect 代理对象，可以监听属性新增、删除、数组索引和 length 变化，也支持 Map、Set 等集合，并且是懒代理。Vue2 使用 Object.defineProperty 递归遍历，无法直接监听新增属性和数组索引。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Vue', '1-3 年', 'Vue3,ref,reactive', 2, 'Vue3 中 ref 和 reactive 有什么区别？如何选择？', 'ref 可以包装基本类型和对象，脚本中通过 .value 访问；reactive 主要用于对象和数组，直接访问属性。reactive 解构会丢失响应性，需要 toRefs。基本类型用 ref，复杂对象可以用 reactive，团队内保持统一风格。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Vue', '1-3 年', 'Vue3,Composition API,Options API', 2, 'Composition API 和 Options API 有什么区别？为什么 Vue3 引入 Composition API？', 'Options API 按 data、methods、生命周期等选项组织代码，简单组件清晰，复杂组件逻辑分散；Composition API 按功能组织逻辑，便于抽取 composable、复用和类型推断。两者可以并存，复杂业务和逻辑复用更适合 Composition API。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Vue', '1-3 年', 'Vue,diff,key', 3, 'Vue 的 diff 算法为什么需要 key？用 index 做 key 有什么问题？', 'key 用于标识节点身份，帮助 diff 复用相同节点，减少 DOM 重建。使用 index 作为 key 时，列表插入或删除会导致后续节点索引变化，可能复用错节点，造成输入框状态错位或不必要的更新。稳定业务 ID 更适合作为 key。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Vue', '校招', 'Vue,生命周期,钩子', 2, 'Vue3 的常用生命周期钩子有哪些？setup 中如何使用？', '常用钩子包括 onBeforeMount、onMounted、onBeforeUpdate、onUpdated、onBeforeUnmount、onUnmounted。setup 在 beforeCreate 和 created 之前执行，因此没有对应的 onBeforeCreate 和 onCreated。请求和 DOM 操作通常放在 onMounted。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Vue', '1-3 年', 'Vue,Pinia,Vuex', 2, 'Pinia 和 Vuex 有什么区别？为什么新项目更推荐 Pinia？', 'Pinia 基于 Composition API 设计，API 更简洁，支持 TypeScript 类型推断，没有 mutations，模块天然扁平化，支持 devtools 和 SSR。Vuex 使用 mutations 修改状态，模块嵌套较复杂。新项目通常优先 Pinia。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Vue', '1-3 年', 'Vue3,script setup,编译宏', 2, 'script setup 有什么优势？defineProps 和 defineEmits 如何使用？', 'script setup 是编译时语法糖，顶层变量和函数自动暴露给模板，减少样板代码，类型推断更好，运行时性能更优。defineProps 声明 props，defineEmits 声明事件，二者都是编译宏，不需要显式导入。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('Vue', '1-3 年', 'Vue,nextTick,DOM更新', 3, 'Vue 的 nextTick 有什么用？什么时候需要使用？', 'Vue 的 DOM 更新是异步批量的，修改响应式数据后立即读取 DOM 可能还是旧值。nextTick 返回一个 Promise，在 DOM 更新完成后执行回调，适合在数据变化后获取元素尺寸、聚焦输入框或调用依赖新 DOM 的第三方库。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('React', '1-3 年', 'React,Fiber,协调', 3, 'React Fiber 解决了什么问题？render 阶段和 commit 阶段有什么区别？', 'Fiber 把同步递归渲染拆成可中断、可恢复、可分优先级的工作单元，避免长任务阻塞主线程。render 阶段计算新 Fiber 树和副作用，可以被打断；commit 阶段把变更应用到真实 DOM，必须同步且不可中断。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('React', '1-3 年', 'React,Hooks,调用规则', 3, '为什么 Hooks 不能写在条件语句或循环中？', 'React 按调用顺序把 Hook 状态存放在组件 Fiber 的链表中，而不是按变量名查找。条件或循环会改变 Hook 调用顺序，导致后续 Hook 状态错位。Rules of Hooks 要求在组件顶层和自定义 Hook 顶层调用。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('React', '1-3 年', 'React,useEffect,useLayoutEffect', 3, 'useEffect 和 useLayoutEffect 有什么区别？', 'useEffect 在浏览器绘制后异步执行，不阻塞渲染；useLayoutEffect 在 DOM 更新后、浏览器绘制前同步执行，适合读取布局并同步修改 DOM。useLayoutEffect 会阻塞绘制，非必要不要使用，SSR 环境中还要注意它不会在服务端执行。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('React', '1-3 年', 'React,useMemo,useCallback', 2, 'useMemo 和 useCallback 分别解决什么问题？什么时候不该用？', 'useMemo 缓存计算结果，useCallback 缓存函数引用，主要用于避免昂贵计算或稳定依赖引用。它们本身也有比较成本，简单计算或依赖频繁变化时使用反而增加开销。React Compiler 普及后应优先写清晰代码。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('React', '1-3 年', 'React,memo,性能优化', 2, 'React.memo 什么时候有用？为什么有时是负优化？', 'React.memo 在 props 浅比较不变时跳过渲染，适合渲染昂贵且 props 稳定的组件。如果 props 每次都是新对象或新函数，浅比较会一直失败，额外的比较和包裹反而增加成本。应结合 Profiler 确认瓶颈再使用。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('React', '1-3 年', 'React,diff,key', 2, 'React 的 diff 算法有哪些特点？key 的作用是什么？', 'React diff 采用同层比较，类型不同直接替换，列表通过 key 识别节点身份，理想复杂度为 O(n)。稳定的 key 能复用 DOM 和组件状态；使用 index 作为 key 在插入删除时可能导致状态错位和不必要的重渲染。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('React', '1-3 年', 'React,批处理,状态更新', 3, 'React 的状态更新为什么需要批处理？React 18 有什么变化？', '批处理把多次状态更新合并为一次渲染，减少重复计算和 DOM 操作。React 18 之前主要在事件处理函数中自动批处理，React 18 通过 createRoot 在 Promise、setTimeout 和原生事件中也自动批处理。需要同步更新时可以用 flushSync。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('React', '1-3 年', 'React,并发渲染,RSC', 3, 'React 18 的并发特性和 React Server Components 解决了什么问题？', '并发渲染允许高优先级更新打断低优先级渲染，改善输入响应，常用 useTransition 和 Suspense。Server Components 在服务端渲染组件、减少客户端 JavaScript 和请求瀑布，适合内容型页面。它们不能替代数据缓存、虚拟列表等结构性优化。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('前端工程化', '1-3 年', 'Vite,Webpack,构建', 2, 'Vite 和 Webpack 的核心区别是什么？', 'Webpack 启动时先打包依赖图，再启动开发服务器；Vite 开发时利用浏览器原生 ES Module 按需编译，冷启动更快，生产环境用 Rollup 打包。Vite 的 HMR 更快，但生态和复杂构建配置仍需按项目评估。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('前端工程化', '1-3 年', 'Tree Shaking,ESM,构建优化', 3, 'Tree Shaking 的原理是什么？为什么有时不生效？', 'Tree Shaking 基于 ES Module 静态分析，标记未使用的导出并在打包时删除。CommonJS 动态 require、副作用声明不清、导出被整体引用或压缩器无法判断时可能失效。需要正确配置 sideEffects 并使用 ESM。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('前端工程化', '1-3 年', '代码分割,懒加载,构建', 2, '代码分割有哪些方式？如何避免首屏包过大？', '可以使用动态 import、路由级懒加载、第三方库按需引入和手动分包。首屏只加载必要代码，把低频页面和重型组件延后加载。还要分析产物体积、避免重复依赖，并配合预加载和缓存策略。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('前端工程化', '校招', 'npm,lockfile,依赖管理', 2, 'package-lock.json 或 pnpm-lock.yaml 有什么作用？', '锁文件记录依赖树的精确版本和完整性校验，保证不同环境和 CI 安装结果一致。提交锁文件可以避免依赖漂移和意外升级。升级依赖要有计划地更新锁文件并跑测试，不要随意删除。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('前端工程化', '校招', 'ESLint,Prettier,代码规范', 2, 'ESLint 和 Prettier 有什么区别？如何配合使用？', 'ESLint 检查代码质量和潜在错误，Prettier 负责统一格式。通常用 ESLint 做静态检查，用 Prettier 做格式化，并通过 eslint-config-prettier 关闭冲突规则。提交前可以用 lint-staged 和 husky 自动检查。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('前端工程化', '1-3 年', 'CI/CD,前端部署,流水线', 3, '前端 CI/CD 流水线一般包含哪些步骤？', '常见步骤包括安装依赖、代码检查、单元测试、构建、产物分析、上传制品、部署到测试环境、自动化验收和灰度发布。要固定 Node 版本、缓存依赖、保留构建产物和回滚版本，并把质量门禁放在合并前。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('前端性能', '1-3 年', '性能,Core Web Vitals,LCP', 3, 'Core Web Vitals 包含哪些指标？如何优化 LCP？', 'Core Web Vitals 主要包括 LCP、INP 和 CLS，分别衡量加载、交互响应和视觉稳定性。优化 LCP 可以压缩首屏资源、预加载关键资源、使用 CDN、减少渲染阻塞和优化图片。要用真实用户监控和实验室数据结合验证。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('前端性能', '1-3 年', '性能,懒加载,图片优化', 2, '前端懒加载有哪些实现方式？图片优化可以做什么？', '可以使用动态 import、路由懒加载、IntersectionObserver 懒加载图片和组件、虚拟列表。图片优化包括选择合适的格式、响应式尺寸、压缩、CDN、懒加载和占位图。懒加载要避免影响首屏关键内容。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('前端性能', '1-3 年', '虚拟列表,长列表,性能', 3, '长列表为什么需要虚拟滚动？实现时要注意什么？', '长列表一次性渲染大量 DOM 会导致内存和布局开销过大，虚拟滚动只渲染可视区域及缓冲区。实现时要处理行高测量、滚动容器、快速滚动白屏、动态高度和键盘可访问性。数据量极大时还要考虑分页和服务端查询。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('前端性能', '1-3 年', 'CDN,缓存,性能', 2, 'CDN 在前端性能优化中起什么作用？缓存策略怎么设计？', 'CDN 把静态资源分发到离用户更近的节点，降低延迟和源站压力。静态资源使用内容哈希和长强缓存，HTML 使用短缓存或协商缓存；CDN 节点缓存时间要小于浏览器缓存时间，发布时通过文件名变化强制更新。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('前端安全', '1-3 年', '安全,XSS,输出转义', 3, 'XSS 有哪几种类型？如何防御？', 'XSS 分为存储型、反射型和 DOM 型，攻击者把脚本注入页面执行。防御包括输入校验和白名单、输出按上下文转义、使用框架默认转义、CSP 限制脚本来源、避免 innerHTML 拼接不可信内容，敏感 Cookie 设置 HttpOnly。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('前端安全', '1-3 年', '安全,CSRF,SameSite', 3, 'CSRF 的原理是什么？如何防御？', 'CSRF 利用用户已登录的 Cookie，诱导浏览器向目标站点发起非预期请求。防御包括 CSRF Token、SameSite Cookie、校验 Origin 或 Referer、敏感操作二次确认。CORS 不能防御 CSRF，因为请求本身仍会到达服务器。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('前端安全', '1-3 年', '安全,CSP,内容安全策略', 3, 'CSP 是什么？如何配置才能降低 XSS 风险？', 'CSP 通过响应头或 meta 限制脚本、样式、图片等资源来源，减少内联脚本和恶意域加载。可以从 default-src self 开始，逐步收紧 script-src、connect-src 和 frame-ancestors，并用 report-only 模式观察误报后再强制。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('前端项目场景', '1-3 年', '项目,登录态,JWT,权限', 3, '前端如何设计登录态和权限控制？', '登录后保存短期 Access Token 和可撤销的 Refresh Token，请求拦截器自动携带并在 401 时刷新。权限分路由权限、菜单权限和按钮权限，前端控制展示、后端做最终鉴权。敏感 Token 优先放 HttpOnly Cookie，并配合 CSRF 防护。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('前端项目场景', '1-3 年', '项目,权限菜单,动态路由', 3, '动态权限菜单如何实现？刷新页面后如何恢复？', '登录后从后端获取用户角色和权限码，前端根据权限过滤路由和菜单，动态注册路由。刷新页面时先恢复用户信息和权限，再挂载路由，避免先渲染无权限页面。按钮级权限用指令或组件封装，后端接口必须再次校验。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('前端项目场景', '1-3 年', '项目,白屏,排查', 3, '线上页面白屏如何排查？', '先看浏览器控制台和 Network，确认是资源加载失败、JS 报错、接口失败还是路由问题；再检查 CDN、缓存、构建产物和版本发布。可以接入前端错误监控和 sourcemap，保留用户环境、路由和请求链路。修复后做灰度验证。', '网络整理-2026-10-28-前端', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT INTO track_question (track_id, question_id, sort_order, weight, enabled, created_at, updated_at)
SELECT
    t.id,
    q.id,
    0,
    120,
    1,
    CURRENT_TIMESTAMP(3),
    CURRENT_TIMESTAMP(3)
FROM question_bank q
JOIN interview_track t ON t.code = 'frontend'
WHERE q.source = '网络整理-2026-10-28-前端'
  AND q.status = 'ACTIVE'
  AND q.deleted = 0;

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
WHERE q.source = '网络整理-2026-10-28-前端'
  AND q.status = 'ACTIVE'
  AND q.deleted = 0;

UPDATE interview_track
SET name = '前端', level = '通用', status = 'ACTIVE', sort_order = 40
WHERE code = 'frontend';

UPDATE job_template
SET title = '前端', level = '通用', status = 'ACTIVE', sort_order = 40
WHERE track_id = (SELECT id FROM interview_track WHERE code = 'frontend');
