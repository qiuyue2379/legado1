(function(e){function t(t){for(var r,o,u=t[0],c=t[1],f=t[2],s=0,l=[];s<u.length;s++)o=u[s],Object.prototype.hasOwnProperty.call(a,o)&&a[o]&&l.push(a[o][0]),a[o]=0;for(r in c)Object.prototype.hasOwnProperty.call(c,r)&&(e[r]=c[r]);d&&d(t);while(l.length)l.shift()();return i.push.apply(i,f||[]),n()}function n(){for(var e,t=0;t<i.length;t++){for(var n=i[t],r=!0,o=1;o<n.length;o++){var u=n[o];0!==a[u]&&(r=!1)}r&&(i.splice(t--,1),e=c(c.s=n[0]))}return e}var r={},o={app:0},a={app:0},i=[];function u(e){return c.p+"js/"+({about:"about",detail:"detail"}[e]||e)+"."+{about:"2f6e7c6f",detail:"ef60e24d"}[e]+".js"}function c(t){if(r[t])return r[t].exports;var n=r[t]={i:t,l:!1,exports:{}};return e[t].call(n.exports,n,n.exports,c),n.l=!0,n.exports}c.e=function(e){var t=[],n={about:1,detail:1};o[e]?t.push(o[e]):0!==o[e]&&n[e]&&t.push(o[e]=new Promise((function(t,n){for(var r="css/"+({about:"about",detail:"detail"}[e]||e)+"."+{about:"65a00131",detail:"dfbc9267"}[e]+".css",a=c.p+r,i=document.getElementsByTagName("link"),u=0;u<i.length;u++){var f=i[u],s=f.getAttribute("data-href")||f.getAttribute("href");if("stylesheet"===f.rel&&(s===r||s===a))return t()}var l=document.getElementsByTagName("style");for(u=0;u<l.length;u++){f=l[u],s=f.getAttribute("data-href");if(s===r||s===a)return t()}var d=document.createElement("link");d.rel="stylesheet",d.type="text/css",d.onload=t,d.onerror=function(t){var r=t&&t.target&&t.target.src||a,i=new Error("Loading CSS chunk "+e+" failed.\n("+r+")");i.code="CSS_CHUNK_LOAD_FAILED",i.request=r,delete o[e],d.parentNode.removeChild(d),n(i)},d.href=a;var p=document.getElementsByTagName("head")[0];p.appendChild(d)})).then((function(){o[e]=0})));var r=a[e];if(0!==r)if(r)t.push(r[2]);else{var i=new Promise((function(t,n){r=a[e]=[t,n]}));t.push(r[2]=i);var f,s=document.createElement("script");s.charset="utf-8",s.timeout=120,c.nc&&s.setAttribute("nonce",c.nc),s.src=u(e);var l=new Error;f=function(t){s.onerror=s.onload=null,clearTimeout(d);var n=a[e];if(0!==n){if(n){var r=t&&("load"===t.type?"missing":t.type),o=t&&t.target&&t.target.src;l.message="Loading chunk "+e+" failed.\n("+r+": "+o+")",l.name="ChunkLoadError",l.type=r,l.request=o,n[1](l)}a[e]=void 0}};var d=setTimeout((function(){f({type:"timeout",target:s})}),12e4);s.onerror=s.onload=f,document.head.appendChild(s)}return Promise.all(t)},c.m=e,c.c=r,c.d=function(e,t,n){c.o(e,t)||Object.defineProperty(e,t,{enumerable:!0,get:n})},c.r=function(e){"undefined"!==typeof Symbol&&Symbol.toStringTag&&Object.defineProperty(e,Symbol.toStringTag,{value:"Module"}),Object.defineProperty(e,"__esModule",{value:!0})},c.t=function(e,t){if(1&t&&(e=c(e)),8&t)return e;if(4&t&&"object"===typeof e&&e&&e.__esModule)return e;var n=Object.create(null);if(c.r(n),Object.defineProperty(n,"default",{enumerable:!0,value:e}),2&t&&"string"!=typeof e)for(var r in e)c.d(n,r,function(t){return e[t]}.bind(null,r));return n},c.n=function(e){var t=e&&e.__esModule?function(){return e["default"]}:function(){return e};return c.d(t,"a",t),t},c.o=function(e,t){return Object.prototype.hasOwnProperty.call(e,t)},c.p="",c.oe=function(e){throw console.error(e),e};var f=window["webpackJsonp"]=window["webpackJsonp"]||[],s=f.push.bind(f);f.push=t,f=f.slice();for(var l=0;l<f.length;l++)t(f[l]);var d=s;i.push([0,"chunk-vendors"]),n()})({0:function(e,t,n){e.exports=n("56d7")},"034f":function(e,t,n){"use strict";n("85ec")},3562:function(e,t,n){e.exports=n.p+"img/loading.550df755.gif"},"56d7":function(e,t,n){"use strict";n.r(t);n("e260"),n("e6cf"),n("cca6"),n("a79d");var r=n("2b0e"),o=function(){var e=this,t=e.$createElement,n=e._self._c||t;return n("div",{attrs:{id:"app"}},[n("router-view")],1)},a=[],i={name:"app",components:{},beforeCreate:function(){var e=this;this.$store.commit("setMiniInterface",window.innerWidth<750),window.onresize=function(){e.$store.commit("setMiniInterface",window.innerWidth<750)}}},u=i,c=(n("034f"),n("2877")),f=Object(c["a"])(u,o,a,!1,null,null,null),s=f.exports,l=(n("d3b7"),n("3ca3"),n("ddb0"),n("8c4f"));r["default"].use(l["a"]);var d=l["a"].prototype.push;l["a"].prototype.push=function(e){return d.call(this,e).catch((function(e){return e}))};var p=[{path:"/",name:"index",component:function(){return n.e("about").then(n.bind(null,"d504"))}},{path:"/chapter",name:"Chapter",component:function(){return n.e("detail").then(n.bind(null,"537b"))}}],h=new l["a"]({base:"",routes:p}),b=h,g=(n("0fb7"),n("450d"),n("f529")),m=n.n(g),v=(n("9e1f"),n("6ed5")),y=n.n(v),w=(n("be4f"),n("896a")),C=n.n(w),S=(n("cbb5"),n("8bbc")),O=n.n(S),j=(n("10cb"),n("f3ad")),k=n.n(j),T=(n("06f1"),n("6ac9")),x=n.n(T),P=(n("5466"),n("ecdf")),_=n.n(P),E=(n("38a0"),n("ad41")),L=n.n(E),$=(n("b84d"),n("c216")),A=n.n($),I=(n("8f24"),n("76b9")),M=n.n(I),B=(n("e3ea"),n("7bc3")),N=n.n(B),R=(n("1951"),n("eedf")),V=n.n(R);r["default"].use(V.a),r["default"].use(N.a),r["default"].use(M.a),r["default"].use(A.a),r["default"].use(L.a),r["default"].use(_.a),r["default"].use(x.a),r["default"].use(k.a),r["default"].use(O.a),r["default"].use(C.a.directive),r["default"].prototype.$msgbox=y.a,r["default"].prototype.$message=m.a,r["default"].prototype.$alert=y.a.alert,r["default"].prototype.$confirm=y.a.confirm,r["default"].prototype.$prompt=y.a.prompt,r["default"].prototype.$loading=C.a.service;var U=n("2f62");r["default"].use(U["a"]);var W=new U["a"].Store({state:{connectStatus:"正在连接后端服务器……",connectType:"",newConnect:!0,shelf:[],catalog:"",readingBook:{},popCataVisible:!1,contentLoading:!0,showContent:!1,config:{theme:0,font:0,fontSize:18,readWidth:800},miniInterface:!1,readSettingsVisible:!1},mutations:{setConnectStatus:function(e,t){e.connectStatus=t},setConnectType:function(e,t){e.connectType=t},setNewConnect:function(e,t){e.newConnect=t},addBooks:function(e,t){e.shelf=t},setCatalog:function(e,t){e.catalog=t},setPopCataVisible:function(e,t){e.popCataVisible=t},setContentLoading:function(e,t){e.contentLoading=t},setReadingBook:function(e,t){e.readingBook=t},setConfig:function(e,t){e.config=t},setReadSettingsVisible:function(e,t){e.readSettingsVisible=t},setShowContent:function(e,t){e.showContent=t},setMiniInterface:function(e,t){e.miniInterface=t}}}),D=(n("be72"),n("b3f5")),J=n("caf9"),q=n("d930");r["default"].config.productionTip=!1,new r["default"]({router:b,store:W,render:function(e){return e(s)}}).$mount("#app"),r["default"].use(J["a"],{preLoad:1.3,error:n("5943"),loading:n("3562"),attempt:1,adapter:{error:function(e){var t=e.src,n=e.el,r=W.state.config.readWidth,o=Object(q["b"])(t,r);null!=o&&(n.src=o)}}}),D["a"].get("/getReadConfig").then((function(e){var t=e.data.data;if(t){var n=JSON.parse(t),r=W.state.config;n=Object.assign(r,n),W.commit("setConfig",n)}}))},5943:function(e,t,n){e.exports=n.p+"img/error.6c7f6bd0.png"},"85ec":function(e,t,n){},b3f5:function(e,t,n){"use strict";n("99af");var r=n("bc3a"),o=n.n(r),a=o.a.create({baseURL:""});t["a"]=a},be72:function(e,t,n){n("d3b7"),n("25f0"),String.prototype.MD5=function(e){var t=this;function n(e,t){return e<<t|e>>>32-t}function r(e,t){var n,r,o,a,i;return o=2147483648&e,a=2147483648&t,n=1073741824&e,r=1073741824&t,i=(1073741823&e)+(1073741823&t),n&r?2147483648^i^o^a:n|r?1073741824&i?3221225472^i^o^a:1073741824^i^o^a:i^o^a}function o(e,t,n){return e&t|~e&n}function a(e,t,n){return e&n|t&~n}function i(e,t,n){return e^t^n}function u(e,t,n){return t^(e|~n)}function c(e,t,a,i,u,c,f){return e=r(e,r(r(o(t,a,i),u),f)),r(n(e,c),t)}function f(e,t,o,i,u,c,f){return e=r(e,r(r(a(t,o,i),u),f)),r(n(e,c),t)}function s(e,t,o,a,u,c,f){return e=r(e,r(r(i(t,o,a),u),f)),r(n(e,c),t)}function l(e,t,o,a,i,c,f){return e=r(e,r(r(u(t,o,a),i),f)),r(n(e,c),t)}function d(e){var t,n=e.length,r=n+8,o=(r-r%64)/64,a=16*(o+1),i=Array(a-1),u=0,c=0;while(c<n)t=(c-c%4)/4,u=c%4*8,i[t]=i[t]|e.charCodeAt(c)<<u,c++;return t=(c-c%4)/4,u=c%4*8,i[t]=i[t]|128<<u,i[a-2]=n<<3,i[a-1]=n>>>29,i}function p(e){var t,n,r="",o="";for(n=0;n<=3;n++)t=e>>>8*n&255,o="0"+t.toString(16),r+=o.substr(o.length-2,2);return r}var h,b,g,m,v,y,w,C,S,O=Array(),j=7,k=12,T=17,x=22,P=5,_=9,E=14,L=20,$=4,A=11,I=16,M=23,B=6,N=10,R=15,V=21;for(O=d(t),y=1732584193,w=4023233417,C=2562383102,S=271733878,h=0;h<O.length;h+=16)b=y,g=w,m=C,v=S,y=c(y,w,C,S,O[h+0],j,3614090360),S=c(S,y,w,C,O[h+1],k,3905402710),C=c(C,S,y,w,O[h+2],T,606105819),w=c(w,C,S,y,O[h+3],x,3250441966),y=c(y,w,C,S,O[h+4],j,4118548399),S=c(S,y,w,C,O[h+5],k,1200080426),C=c(C,S,y,w,O[h+6],T,2821735955),w=c(w,C,S,y,O[h+7],x,4249261313),y=c(y,w,C,S,O[h+8],j,1770035416),S=c(S,y,w,C,O[h+9],k,2336552879),C=c(C,S,y,w,O[h+10],T,4294925233),w=c(w,C,S,y,O[h+11],x,2304563134),y=c(y,w,C,S,O[h+12],j,1804603682),S=c(S,y,w,C,O[h+13],k,4254626195),C=c(C,S,y,w,O[h+14],T,2792965006),w=c(w,C,S,y,O[h+15],x,1236535329),y=f(y,w,C,S,O[h+1],P,4129170786),S=f(S,y,w,C,O[h+6],_,3225465664),C=f(C,S,y,w,O[h+11],E,643717713),w=f(w,C,S,y,O[h+0],L,3921069994),y=f(y,w,C,S,O[h+5],P,3593408605),S=f(S,y,w,C,O[h+10],_,38016083),C=f(C,S,y,w,O[h+15],E,3634488961),w=f(w,C,S,y,O[h+4],L,3889429448),y=f(y,w,C,S,O[h+9],P,568446438),S=f(S,y,w,C,O[h+14],_,3275163606),C=f(C,S,y,w,O[h+3],E,4107603335),w=f(w,C,S,y,O[h+8],L,1163531501),y=f(y,w,C,S,O[h+13],P,2850285829),S=f(S,y,w,C,O[h+2],_,4243563512),C=f(C,S,y,w,O[h+7],E,1735328473),w=f(w,C,S,y,O[h+12],L,2368359562),y=s(y,w,C,S,O[h+5],$,4294588738),S=s(S,y,w,C,O[h+8],A,2272392833),C=s(C,S,y,w,O[h+11],I,1839030562),w=s(w,C,S,y,O[h+14],M,4259657740),y=s(y,w,C,S,O[h+1],$,2763975236),S=s(S,y,w,C,O[h+4],A,1272893353),C=s(C,S,y,w,O[h+7],I,4139469664),w=s(w,C,S,y,O[h+10],M,3200236656),y=s(y,w,C,S,O[h+13],$,681279174),S=s(S,y,w,C,O[h+0],A,3936430074),C=s(C,S,y,w,O[h+3],I,3572445317),w=s(w,C,S,y,O[h+6],M,76029189),y=s(y,w,C,S,O[h+9],$,3654602809),S=s(S,y,w,C,O[h+12],A,3873151461),C=s(C,S,y,w,O[h+15],I,530742520),w=s(w,C,S,y,O[h+2],M,3299628645),y=l(y,w,C,S,O[h+0],B,4096336452),S=l(S,y,w,C,O[h+7],N,1126891415),C=l(C,S,y,w,O[h+14],R,2878612391),w=l(w,C,S,y,O[h+5],V,4237533241),y=l(y,w,C,S,O[h+12],B,1700485571),S=l(S,y,w,C,O[h+3],N,2399980690),C=l(C,S,y,w,O[h+10],R,4293915773),w=l(w,C,S,y,O[h+1],V,2240044497),y=l(y,w,C,S,O[h+8],B,1873313359),S=l(S,y,w,C,O[h+15],N,4264355552),C=l(C,S,y,w,O[h+6],R,2734768916),w=l(w,C,S,y,O[h+13],V,1309151649),y=l(y,w,C,S,O[h+4],B,4149444226),S=l(S,y,w,C,O[h+11],N,3174756917),C=l(C,S,y,w,O[h+2],R,718787259),w=l(w,C,S,y,O[h+9],V,3951481745),y=r(y,b),w=r(w,g),C=r(C,m),S=r(S,v);return 32==e?p(y)+p(w)+p(C)+p(S):p(w)+p(C)}},d930:function(e,t,n){"use strict";n.d(t,"b",(function(){return r})),n.d(t,"a",(function(){return o}));n("ac1f"),n("00b4");function r(e,t){return/cover\?path=|data:/.test(e)?null:"../../image?path="+encodeURIComponent(e)+"&url="+encodeURIComponent(sessionStorage.getItem("bookUrl"))+"&width="+t}function o(e){var t=arguments.length>1&&void 0!==arguments[1]?arguments[1]:250,n=null;return function(){var r=this,o=arguments;n&&clearTimeout(n),n=setTimeout((function(){e.apply(r,o)}),t)}}}});