import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Main {
    public static void main(String[] args) throws Exception {
        EmployeeStore store = new EmployeeStore(new File("employees.tsv"));
        store.load();
        UserStore users = new UserStore(new File("users.tsv"));
        users.loadOrInitDefault();
        SessionManager session = new SessionManager();
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", ex -> handleIndex(ex, session));
        server.createContext("/login", ex -> handleLoginPage(ex));
        server.createContext("/api/login", ex -> handleLogin(ex, users, session));
        server.createContext("/api/logout", ex -> handleLogout(ex, session));
        server.createContext("/api/employees", ex -> handleEmployees(ex, store, session));
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.out.println("员工信息系统已启动: http://localhost:8080/");
    }

    // UI 页面：表单 + 列表
    static void handleIndex(HttpExchange ex, SessionManager session) throws IOException {
        String sid = parseCookies(ex).get("SID");
        String user = session.getUser(sid);
        if (user == null) {
            ex.getResponseHeaders().set("Location", "/login");
            ex.sendResponseHeaders(302, -1);
            ex.getResponseBody().close();
            return;
        }
        // 原来的页面内容保持不变（淡蓝主题 + 弹窗），直接复用
        String html = ""+
                "<!doctype html>\n"+
                "<html lang=\"zh-CN\">\n"+
                "<head>\n"+
                "  <meta charset=\"utf-8\"/>\n"+
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>\n"+
                "  <title>员工信息系统</title>\n"+
                "  <style>:root{--bg:#eaf4ff;--accent:#1f6feb;--text:#0a1f44;--card:#ffffff;--border:#d7e3f8}*{box-sizing:border-box}body{font-family:system-ui,-apple-system,BlinkMacSystemFont,\"Segoe UI\",sans-serif;margin:0;background:var(--bg);color:var(--text);} .header{background:var(--card);border-bottom:1px solid var(--border);padding:16px 24px;display:flex;align-items:center;justify-content:space-between;position:sticky;top:0;} .header h2{margin:0;font-weight:600;color:#123} .header .actions button{background:var(--accent);color:#fff;border:none;border-radius:6px;padding:8px 14px;cursor:pointer;box-shadow:0 2px 6px rgba(31,111,235,.15)} .container{max-width:1000px;margin:24px auto;padding:0 24px;} .table-card{background:var(--card);border:1px solid var(--border);border-radius:10px;padding:16px;box-shadow:0 6px 16px rgba(17,38,97,.06);} table{border-collapse:collapse;width:100%;} th,td{border-bottom:1px solid var(--border);padding:10px;text-align:left;} th{font-weight:600;color:#345} tr:hover{background:#f5faff} .badge{font-size:12px;color:#567} #msg{color:#0a0;margin-left:10px} .modal-backdrop{position:fixed;inset:0;background:rgba(18,33,64,.35);display:none;align-items:center;justify-content:center;padding:16px;} .modal{width:520px;max-width:100%;background:#fff;border-radius:12px;box-shadow:0 16px 40px rgba(0,0,0,.2);overflow:hidden;border:1px solid var(--border);} .modal header{padding:14px 16px;border-bottom:1px solid var(--border);display:flex;justify-content:space-between;align-items:center} .modal header h3{margin:0} .modal .body{padding:16px} .form-row{display:flex;gap:12px;margin-bottom:12px} .form-row label{flex:1;display:flex;flex-direction:column;font-size:13px;color:#345} .form-row input{margin-top:6px;padding:8px 10px;border:1px solid var(--border);border-radius:8px} .modal footer{padding:12px 16px;border-top:1px solid var(--border);display:flex;justify-content:flex-end;gap:12px} .btn{padding:8px 14px;border-radius:8px;border:1px solid var(--border);background:#fff;color:#123;cursor:pointer} .btn.primary{background:var(--accent);border-color:var(--accent);color:#fff} .btn.danger{background:#c62828;border-color:#c62828;color:#fff} .hidden{display:none}</style>\n"+
                "</head>\n"+
                "<body>\n"+
                "  <div class=\"header\">\n"+
                "    <h2>公司招聘员工管理</h2>\n"+
                "    <div class=\"actions\">\n"+
                "      <button class=\"btn primary\" onclick=\"openAddModal()\">新增员工</button>\n"+
                "      <span class=\"badge\" id=\"msg\"></span>\n"+
                "      <button class=\"btn\" onclick=\"logout()\" style=\"margin-left:8px;\">退出登录</button>\n"+
                "    </div>\n"+
                "  </div>\n"+
                "  <div class=\"container\">\n"+
                "    <div class=\"table-card\">\n"+
                "      <h3 style=\"margin:0 0 10px 0;\">员工列表</h3>\n"+
                "      <table><thead><tr><th>ID</th><th>姓名</th><th>年龄</th><th>部门</th><th>职位</th><th>邮箱</th><th>电话</th><th>操作</th></tr></thead><tbody id=\"rows\"></tbody></table>\n"+
                "    </div>\n"+
                "  </div>\n"+
                "  <div class=\"modal-backdrop\" id=\"modalBackdrop\">\n"+
                "    <div class=\"modal\">\n"+
                "      <header>\n"+
                "        <h3 id=\"modalTitle\">新增员工</h3>\n"+
                "        <button class=\"btn\" onclick=\"closeModal()\">关闭</button>\n"+
                "      </header>\n"+
                "      <div class=\"body\">\n"+
                "        <div class=\"form-row\">\n"+
                "          <label>姓名<input id=\"m_name\" /></label>\n"+
                "          <label>年龄<input id=\"m_age\" type=\"number\" /></label>\n"+
                "        </div>\n"+
                "        <div class=\"form-row\">\n"+
                "          <label>部门<input id=\"m_dept\" /></label>\n"+
                "          <label>职位<input id=\"m_position\" /></label>\n"+
                "        </div>\n"+
                "        <div class=\"form-row\">\n"+
                "          <label>邮箱<input id=\"m_email\" /></label>\n"+
                "          <label>电话<input id=\"m_phone\" maxlength=\"11\" oninput=\"digitsOnly(this)\" /></label>\n"+
                "        </div>\n"+
                "        <input type=\"hidden\" id=\"m_id\"/>\n"+
                "      </div>\n"+
                "      <footer>\n"+
                "        <button class=\"btn\" onclick=\"closeModal()\">取消</button>\n"+
                "        <button class=\"btn primary\" id=\"modalSubmitBtn\" onclick=\"submitModal()\">保存</button>\n"+
                "      </footer>\n"+
                "    </div>\n"+
                "  </div>\n"+
                "  <div class=\"modal-backdrop\" id=\"viewBackdrop\" style=\"display:none;\">\n"+
                "    <div class=\"modal\">\n"+
                "      <header>\n"+
                "        <h3>员工详情</h3>\n"+
                "        <button class=\"btn\" onclick=\"closeView()\">关闭</button>\n"+
                "      </header>\n"+
                "      <div class=\"body\">\n"+
                "        <div class=\"form-row\">\n"+
                "          <label>姓名<span id=\"v_name\"></span></label>\n"+
                "          <label>年龄<span id=\"v_age\"></span></label>\n"+
                "        </div>\n"+
                "        <div class=\"form-row\">\n"+
                "          <label>部门<span id=\"v_dept\"></span></label>\n"+
                "          <label>职位<span id=\"v_position\"></span></label>\n"+
                "        </div>\n"+
                "        <div class=\"form-row\">\n"+
                "          <label>邮箱<span id=\"v_email\"></span></label>\n"+
                "          <label>电话<span id=\"v_phone\"></span></label>\n"+
                "        </div>\n"+
                "      </div>\n"+
                "      <footer>\n"+
                "        <button class=\"btn\" onclick=\"closeView()\">关闭</button>\n"+
                "      </footer>\n"+
                "    </div>\n"+
                "  </div>\n"+
                "  <script>\n"+
                "    function digitsOnly(el){ el.value = el.value.replace(/\\D/g,'').slice(0,11); }\n"+
                "    function maskPhone(p){ if(!p) return ''; const s = (''+p).replace(/\\D/g,''); if(s.length===11) return s.slice(0,3)+'****'+s.slice(7); return p; }\n"+
                "    function openViewModal(btn){ setViewFields(btn.dataset.name||'', btn.dataset.age||'', btn.dataset.dept||'', btn.dataset.position||'', btn.dataset.email||'', btn.dataset.phone||''); showView(true); }\n"+
                "    function setViewFields(name, age, dept, position, email, phone){ document.getElementById('v_name').textContent=name; document.getElementById('v_age').textContent=age; document.getElementById('v_dept').textContent=dept; document.getElementById('v_position').textContent=position; document.getElementById('v_email').textContent=email; document.getElementById('v_phone').textContent=phone; }\n"+
                "    function showView(show){ document.getElementById('viewBackdrop').style.display = show ? 'flex' : 'none'; }\n"+
                "    function closeView(){ showView(false); }\n"+
                "    async function list(){\n"+
                "      const res = await fetch('/api/employees');\n"+
                "      if(res.status===401){ location.href='/login'; return; }\n"+
                "      const data = await res.json();\n"+
                "      const tbody = document.getElementById('rows');\n"+
                "      tbody.innerHTML = '';\n"+
                "      for(const e of data){\n"+
                "        const tr = document.createElement('tr');\n"+
                "        tr.innerHTML = `<td>${e.id}</td><td>${e.name}</td><td>${e.age}</td><td>${e.dept}</td><td>${e.position||''}</td><td>${e.email}</td><td>${maskPhone(e.phone)}</td>\n"+
                "          <td><button class=\"btn\" onclick=\"openViewModal(this)\" data-id=\"${e.id}\" data-name=\"${e.name}\" data-age=\"${e.age}\" data-dept=\"${e.dept}\" data-position=\"${e.position||''}\" data-email=\"${e.email}\" data-phone=\"${e.phone}\">查看</button> <button class=\"btn\" onclick=\"openEditModal(this)\" data-id=\"${e.id}\" data-name=\"${e.name}\" data-age=\"${e.age}\" data-dept=\"${e.dept}\" data-position=\"${e.position||''}\" data-email=\"${e.email}\" data-phone=\"${e.phone}\">编辑</button> <button class=\"btn danger\" onclick=\"delEmp('${e.id}')\">删除</button></td>`;\n"+
                "        tbody.appendChild(tr);\n"+
                "      }\n"+
                "    }\n"+
                "    function openAddModal(){\n"+
                "      document.getElementById('modalTitle').textContent='新增员工';\n"+
                "      document.getElementById('m_id').value='';\n"+
                "      setModalFields('', '', '', '', '', '');\n"+
                "      showModal(true);\n"+
                "    }\n"+
                "    function openEditModal(btn){\n"+
                "      document.getElementById('modalTitle').textContent='编辑员工';\n"+
                "      document.getElementById('m_id').value=btn.dataset.id||'';\n"+
                "      setModalFields(btn.dataset.name||'', btn.dataset.age||'', btn.dataset.dept||'', btn.dataset.position||'', btn.dataset.email||'', btn.dataset.phone||'');\n"+
                "      showModal(true);\n"+
                "    }\n"+
                "    function setModalFields(name, age, dept, position, email, phone){\n"+
                "      document.getElementById('m_name').value=name;\n"+
                "      document.getElementById('m_age').value=age;\n"+
                "      document.getElementById('m_dept').value=dept;\n"+
                "      document.getElementById('m_position').value=position;\n"+
                "      document.getElementById('m_email').value=email;\n"+
                "      document.getElementById('m_phone').value=phone;\n"+
                "    }\n"+
                "    function showModal(show){ document.getElementById('modalBackdrop').style.display = show ? 'flex' : 'none'; }\n"+
                "    function closeModal(){ showModal(false); }\n"+
                "    async function submitModal(){\n"+
                "      const id = document.getElementById('m_id').value;\n"+
                "      const fd = new URLSearchParams();\n"+
                "      fd.set('name', document.getElementById('m_name').value);\n"+
                "      fd.set('age', document.getElementById('m_age').value);\n"+
                "      fd.set('dept', document.getElementById('m_dept').value);\n"+
                "      fd.set('position', document.getElementById('m_position').value);\n"+
                "      fd.set('email', document.getElementById('m_email').value);\n"+
                "      const phoneRaw = document.getElementById('m_phone').value.replace(/\\D/g,'');\n"+
                "      if(phoneRaw.length !== 11){ document.getElementById('msg').textContent = '电话需为11位数字'; return; }\n"+
                "      fd.set('phone', phoneRaw);\n"+
                "      if(id){ fd.set('action','update'); fd.set('id', id); } else { fd.set('action','add'); }\n"+
                "      const res = await fetch('/api/employees', {method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:fd});\n"+
                "      const t = await res.text(); document.getElementById('msg').textContent = t; closeModal(); list();\n"+
                "    }\n"+
                "    async function delEmp(id){\n"+
                "      const fd = new URLSearchParams(); fd.set('action','delete'); fd.set('id', id);\n"+
                "      const res = await fetch('/api/employees', {method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:fd});\n"+
                "      const t = await res.text(); document.getElementById('msg').textContent = t; list();\n"+
                "    }\n"+
                "    async function logout(){\n"+
                "      await fetch('/api/logout', {method:'POST'}); location.href='/login';\n"+
                "    }\n"+
                "    list();\n"+
                "  </script>\n"+
                "</body>\n"+
                "</html>\n";
        writeUtf8(ex, 200, "text/html; charset=utf-8", html);
    }

    // API：GET 列表；POST add/update/delete
    static void handleEmployees(HttpExchange ex, EmployeeStore store, SessionManager session) throws IOException {
        String sid = parseCookies(ex).get("SID");
        String user = session.getUser(sid);
        if (user == null) { writeUtf8(ex, 401, ctText(), "未登录"); return; }
        // 原逻辑保持
        String method = ex.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            List<Map<String,Object>> list = new ArrayList<>();
            for (Employee e : store.list()) {
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("id", e.id);
                m.put("name", e.name);
                m.put("age", e.age);
                m.put("dept", e.dept);
                m.put("email", e.email);
                m.put("phone", e.phone);
                m.put("position", e.position);
                list.add(m);
            }
            writeUtf8(ex, 200, "application/json; charset=utf-8", toJsonArray(list));
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String,String> form = parseForm(body);
            String action = form.getOrDefault("action", "");
            switch (action) {
                case "add": {
                    String id = UUID.randomUUID().toString();
                    Employee e = new Employee();
                    e.id = id;
                    e.name = form.getOrDefault("name", "");
                    e.age = parseInt(form.get("age"), 0);
                    e.dept = form.getOrDefault("dept", "");
                    e.position = form.getOrDefault("position", "");
                    e.email = form.getOrDefault("email", "");
                    e.phone = form.getOrDefault("phone", "");
                    store.put(e); store.save();
                    writeUtf8(ex, 200, "text/plain; charset=utf-8", "添加成功: "+id);
                    return;
                }
                case "update": {
                    String id = form.get("id"); if (id==null || id.isEmpty()) { writeUtf8(ex, 400, ctText(), "缺少id"); return; }
                    Employee e = store.get(id); if (e==null) { writeUtf8(ex, 404, ctText(), "未找到: "+id); return; }
                    e.name = form.getOrDefault("name", e.name);
                    e.age = parseInt(form.get("age"), e.age);
                    e.dept = form.getOrDefault("dept", e.dept);
                    e.position = form.getOrDefault("position", e.position);
                    e.email = form.getOrDefault("email", e.email);
                    e.phone = form.getOrDefault("phone", e.phone);
                    store.put(e); store.save();
                    writeUtf8(ex, 200, ctText(), "更新成功: "+id);
                    return;
                }
                case "delete": {
                    String id = form.get("id"); if (id==null || id.isEmpty()) { writeUtf8(ex, 400, ctText(), "缺少id"); return; }
                    store.remove(id); store.save();
                    writeUtf8(ex, 200, ctText(), "删除成功: "+id);
                    return;
                }
                default:
                    writeUtf8(ex, 400, ctText(), "未知操作");
            }
        } else {
            writeUtf8(ex, 405, ctText(), "仅支持GET/POST");
        }
    }

    // 工具方法
    static String ctText() { return "text/plain; charset=utf-8"; }
    static void writeUtf8(HttpExchange ex, int code, String ct, String body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", ct);
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, data.length);
        try(OutputStream os = ex.getResponseBody()) { os.write(data); }
    }
    static Map<String,String> parseForm(String body) {
        Map<String,String> m = new HashMap<>();
        if (body==null || body.isEmpty()) return m;
        for (String kv : body.split("&")) {
            int i = kv.indexOf('=');
            if (i>0) m.put(urlDecode(kv.substring(0,i)), urlDecode(kv.substring(i+1)));
            else m.put(urlDecode(kv), "");
        }
        return m;
    }
    static String urlDecode(String s){ return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8); }
    static int parseInt(String s, int def){ try { return Integer.parseInt(s); } catch(Exception e){ return def; } }

    // 模型与持久化
    static class Employee { String id, name, dept, position, email, phone; int age; }

    static class EmployeeStore {
        private final File file;
        private final ConcurrentHashMap<String, Employee> map = new ConcurrentHashMap<>();
        EmployeeStore(File file){ this.file = file; }
        synchronized void load() throws IOException {
            if (!file.exists()) return;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    String[] arr = line.split("\t", -1);
                    if (arr.length < 7) continue;
                    Employee e = new Employee();
                    e.id=arr[0]; e.name=arr[1]; e.age=parseInt(arr[2],0); e.dept=arr[3]; e.email=arr[4]; e.phone=arr[5];
                    e.position = (arr.length>=7 ? arr[6] : "");
                    map.put(e.id, e);
                }
            }
        }
        synchronized void save() throws IOException {
            File tmp = new File(file.getParentFile()==null?".":file.getParentFile().getPath(), file.getName()+".tmp");
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
                for (Employee e : map.values()) {
                    bw.write(String.join("\t",
                            e.id==null?"":e.id,
                            e.name==null?"":e.name,
                            String.valueOf(e.age),
                            e.dept==null?"":e.dept,
                            e.position==null?"":e.position,
                            e.email==null?"":e.email,
                            e.phone==null?"":e.phone
                    ));
                    bw.write("\n");
                }
            }
            if (file.exists()) file.delete();
            tmp.renameTo(file);
        }
        synchronized Collection<Employee> list(){ return new ArrayList<>(map.values()); }
        synchronized Employee get(String id){ return map.get(id); }
        synchronized void put(Employee e){ map.put(e.id, e); }
        synchronized void remove(String id){ map.remove(id); }
    }

    // 简单 JSON 输出（只用于列表）
    static String toJsonArray(List<Map<String,Object>> list){
        StringBuilder sb = new StringBuilder();
        sb.append("["); boolean first=true;
        for (Map<String,Object> m : list){ if(!first) sb.append(','); first=false; sb.append(toJsonObject(m)); }
        sb.append("]"); return sb.toString();
    }
    static String toJsonObject(Map<String,Object> m){
        StringBuilder sb = new StringBuilder(); sb.append("{"); boolean first=true;
        for (Map.Entry<String,Object> e : m.entrySet()){
            if(!first) sb.append(','); first=false;
            sb.append('"').append(escape(e.getKey())).append('"').append(':');
            Object v = e.getValue();
            if (v==null) sb.append("null");
            else if (v instanceof Number) sb.append(v.toString());
            else sb.append('"').append(escape(String.valueOf(v))).append('"');
        }
        sb.append('}'); return sb.toString();
    }
    static String escape(String s){ return s.replace("\\","\\\\").replace("\"","\\\""); }
 
 // 登录页面
 static void handleLoginPage(HttpExchange ex) throws IOException {
    String html = ""+
            "<!doctype html>\n"+
            "<html lang=\"zh-CN\">\n"+
            "<head>\n"+
            "  <meta charset=\"utf-8\"/>\n"+
            "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>\n"+
            "  <title>登录 - 员工信息系统</title>\n"+
            "  <style>:root{--bg:#eaf4ff;--accent:#1f6feb;--text:#0a1f44;--card:#ffffff;--border:#d7e3f8}*{box-sizing:border-box}body{font-family:system-ui,-apple-system,BlinkMacSystemFont,\"Segoe UI\",sans-serif;margin:0;background:var(--bg);color:var(--text);} .wrap{display:flex;min-height:100vh;align-items:center;justify-content:center;padding:24px} .card{width:400px;max-width:100%;background:#fff;border:1px solid var(--border);border-radius:12px;box-shadow:0 10px 30px rgba(0,0,0,.08);padding:20px} h2{margin:0 0 14px 0} .form{display:flex;flex-direction:column;gap:12px} label{font-size:13px;color:#345;display:flex;flex-direction:column} input{margin-top:6px;padding:10px;border:1px solid var(--border);border-radius:8px} .btn{padding:10px;border-radius:8px;border:none;background:var(--accent);color:#fff;cursor:pointer} .msg{margin-top:10px;color:#c62828} </style>\n"+
            "</head>\n"+
            "<body>\n"+
            "  <div class=\"wrap\">\n"+
            "    <div class=\"card\">\n"+
            "      <h2>登录员工管理系统</h2>\n"+
            "      <div class=\"form\">\n"+
            "        <label>用户名<input id=\"u\"/></label>\n"+
            "        <label>密码<input id=\"p\" type=\"password\"/></label>\n"+
            "        <button class=\"btn\" onclick=\"doLogin()\">登录</button>\n"+
            "        <div class=\"msg\" id=\"m\"></div>\n"+
            "      </div>\n"+
            "    </div>\n"+
            "  </div>\n"+
            "  <script>async function doLogin(){ const fd=new URLSearchParams(); fd.set('username',document.getElementById('u').value); fd.set('password',document.getElementById('p').value); const r=await fetch('/api/login',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:fd}); if(r.ok){ location.href='/'; } else { document.getElementById('m').textContent=await r.text(); } }</script>\n"+
            "</body>\n"+
            "</html>\n";
    writeUtf8(ex, 200, "text/html; charset=utf-8", html);
}

// 登录/登出接口
static void handleLogin(HttpExchange ex, UserStore users, SessionManager session) throws IOException {
    if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { writeUtf8(ex, 405, ctText(), "仅支持POST"); return; }
    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    Map<String,String> form = parseForm(body);
    String u = form.getOrDefault("username", "");
    String p = form.getOrDefault("password", "");
    if (users.verify(u, p)) {
        String sid = UUID.randomUUID().toString();
        session.put(sid, u);
        setCookie(ex, "SID", sid, "/", 7*24*3600);
        writeUtf8(ex, 200, ctText(), "登录成功");
    } else {
        writeUtf8(ex, 401, ctText(), "用户名或密码错误");
    }
}
static void handleLogout(HttpExchange ex, SessionManager session) throws IOException {
    String sid = parseCookies(ex).get("SID");
    if (sid != null) session.remove(sid);
    setCookie(ex, "SID", "", "/", 0);
    writeUtf8(ex, 200, ctText(), "已退出");
}

// Cookie 与会话工具
static Map<String,String> parseCookies(HttpExchange ex){
    String h = ex.getRequestHeaders().getFirst("Cookie");
    Map<String,String> m = new HashMap<>();
    if (h==null || h.isEmpty()) return m;
    for (String part : h.split(";")){
        String s = part.trim();
        int i = s.indexOf('=');
        if (i>0) m.put(s.substring(0,i), s.substring(i+1));
    }
    return m;
}
static void setCookie(HttpExchange ex, String name, String val, String path, int maxAge){
    StringBuilder sb = new StringBuilder();
    sb.append(name).append("=").append(val==null?"":val).append("; Path=").append(path==null?"/":path);
    if (maxAge>0) sb.append("; Max-Age=").append(maxAge); else sb.append("; Max-Age=0");
    sb.append("; HttpOnly");
    ex.getResponseHeaders().add("Set-Cookie", sb.toString());
}

// 用户存储与会话管理
static class UserStore {
    private final File file;
    private final ConcurrentHashMap<String,String> map = new ConcurrentHashMap<>();
    UserStore(File file){ this.file = file; }
    synchronized void loadOrInitDefault() throws IOException {
        if (!file.exists()) {
            put("admin", "admin123"); save();
            return;
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line; while((line=br.readLine())!=null){ if(line.trim().isEmpty()) continue; String[] a=line.split("\t",-1); if(a.length<2) continue; map.put(a[0], a[1]); }
        }
    }
    synchronized void save() throws IOException {
        File tmp = new File(file.getParentFile()==null?".":file.getParentFile().getPath(), file.getName()+".tmp");
        try(BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))){ for (Map.Entry<String,String> e : map.entrySet()){ bw.write(e.getKey()+"\t"+e.getValue()+"\n"); } }
        if (file.exists()) file.delete(); tmp.renameTo(file);
    }
    synchronized void put(String u, String p){ map.put(u, p); }
    synchronized boolean verify(String u, String p){ String v = map.get(u); return v!=null && v.equals(p); }
}
static class SessionManager {
    private final ConcurrentHashMap<String,String> sessions = new ConcurrentHashMap<>();
    void put(String sid, String user){ sessions.put(sid, user); }
    String getUser(String sid){ return sid==null?null:sessions.get(sid); }
    void remove(String sid){ if(sid!=null) sessions.remove(sid); }
}
}