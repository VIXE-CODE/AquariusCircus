import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class RunPostman {
    static final String PREFIX="aquarius_postman_test_";
    static Properties config=new Properties();
    static String user,password,host;
    static int databasePort;
    static String url(String database){return "jdbc:postgresql://"+host+":"+databasePort+"/"+database;}
    static Connection connect(String database)throws SQLException{return DriverManager.getConnection(url(database),user,password);}
    static String value(String key,String fallback){
        String env=System.getenv(key);
        return env!=null?env:config.getProperty(key,fallback);
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=8)throw new IllegalArgumentException("Argumentos do executor incompletos.");
        Path project=Path.of(args[0]).toAbsolutePath();
        Path jar=Path.of(args[1]).toAbsolutePath();
        String node=args[2],newman=args[3];
        Path bundle=Path.of(args[4]).toAbsolutePath(),runtime=Path.of(args[5]).toAbsolutePath();
        int port=Integer.parseInt(args[6]);
        Path reports=Path.of(args[7]).toAbsolutePath();
        if(port==8080||port<1024||port>65535)throw new IllegalArgumentException("Use outra porta de teste, diferente de 8080.");
        try(var socket=new java.net.ServerSocket()){socket.bind(new InetSocketAddress("127.0.0.1",port));}
        for(int extraPort:new int[]{2526,8091})try(var socket=new java.net.ServerSocket()){
            socket.bind(new InetSocketAddress("127.0.0.1",extraPort));
        }
        Path local=project.resolve(".env.properties");
        if(Files.exists(local))try(var reader=Files.newBufferedReader(local,StandardCharsets.UTF_8)){config.load(reader);}
        URI source=URI.create(value("DB_URL","jdbc:postgresql://localhost:5432/gestao_alunos").substring(5));
        host=source.getHost(); databasePort=source.getPort()<0?5432:source.getPort();
        if(!List.of("localhost","127.0.0.1").contains(host))throw new IllegalArgumentException("Este executor aceita somente PostgreSQL local.");
        user=value("DB_USERNAME","postgres");password=value("DB_PASSWORD",null);
        if(password==null)throw new IllegalArgumentException("Configure DB_PASSWORD localmente. Não coloque a senha na coleção.");
        String database=PREFIX+LocalDate.now().toString().replace("-","")+"_"+UUID.randomUUID().toString().substring(0,8);
        if(!database.matches("aquarius_postman_test_[0-9]{8}_[a-f0-9]{8}"))throw new IllegalStateException("Nome de banco inesperado.");
        Files.createDirectories(runtime);Files.createDirectories(reports);
        try(Connection connection=connect("postgres"); Statement stmt=connection.createStatement()){
            stmt.executeUpdate("CREATE DATABASE \""+database+"\"");
            System.out.println("PostgreSQL de teste criado: "+database+" (versão "+connection.getMetaData().getDatabaseProductVersion()+")");
        }
        String java=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        var builder=new ProcessBuilder(java,"-jar",jar.toString(),"--server.address=127.0.0.1","--server.port="+port,
            "--spring.datasource.url="+url(database),"--spring.datasource.username="+user,
            "--spring.jpa.hibernate.ddl-auto=update","--spring.profiles.active=postman");
        String secret=UUID.randomUUID().toString()+UUID.randomUUID();
        builder.environment().put("DB_PASSWORD",password);
        builder.environment().put("SPRING_DATASOURCE_PASSWORD",password);
        builder.environment().put("JWT_SECRET",secret);
        builder.environment().put("API_SECURITY_TOKEN_SECRET",secret);
        builder.environment().put("APP_TIME_ZONE","America/Sao_Paulo");
        builder.environment().put("SMTP_HOST","127.0.0.1");
        builder.environment().put("SMTP_PORT","2526");
        builder.environment().put("SMTP_AUTH","false");
        builder.environment().put("SMTP_STARTTLS","false");
        builder.environment().put("SMTP_FROM","recuperacao@aquarius.test");
        builder.environment().put("PASSWORD_RESET_URL","http://localhost:5173/redefinir-senha");
        builder.directory(runtime.toFile());builder.redirectErrorStream(true);builder.redirectOutput(runtime.resolve("api-postman.log").toFile());
        Process app=builder.start();
        try {
            String base="http://127.0.0.1:"+port;
            HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            boolean ready=false;
            for(int i=0;i<90;i++){
                if(!app.isAlive())throw new IllegalStateException("API de teste encerrou. Consulte "+runtime.resolve("api-postman.log"));
                try{var response=client.send(HttpRequest.newBuilder(URI.create(base+"/api/planos")).timeout(Duration.ofSeconds(2)).GET().build(),HttpResponse.BodyHandlers.discarding());
                    if(response.statusCode()==200){ready=true;break;}}
                catch(IOException ignored){}
                Thread.sleep(500);
            }
            if(!ready)throw new IllegalStateException("A API isolada não iniciou a tempo.");
            try(Connection c=connect(database);PreparedStatement stmt=c.prepareStatement(
                "INSERT INTO usuarios (id,nome,email,senha,role) VALUES (?,?,?,?,?)")){
                stmt.setObject(1,UUID.randomUUID());stmt.setString(2,"Professor fictício Postman");
                stmt.setString(3,"professor.postman@aquarius.test");
                stmt.setString(4,new BCryptPasswordEncoder().encode("AquariusTeste123!"));stmt.setString(5,"PROFESSOR");
                stmt.executeUpdate();
            }
            System.out.println("API isolada pronta na porta "+port+"; iniciando coleção Postman.");
            Process runner=new ProcessBuilder(node,bundle.resolve("executar-newman.cjs").toString(),newman,
                bundle.resolve("Aquarius.postman_collection.json").toString(),
                bundle.resolve("Aquarius-Teste.postman_environment.json").toString(),
                reports.toString(),base,database).inheritIO().start();
            int exit=runner.waitFor();
            boolean invariants=verify(database,reports);
            if(exit!=0||!invariants)throw new IllegalStateException("Uma ou mais verificações falharam. Consulte os relatórios.");
            System.out.println("Coleção concluída e invariantes do PostgreSQL aprovadas.");
        } finally {
            app.destroy();if(!app.waitFor(10,TimeUnit.SECONDS))app.destroyForcibly();
            System.out.println("API de teste encerrada. Banco fictício mantido para inspeção: "+database);
        }
    }
    static boolean verify(String database,Path reports)throws Exception{
        Map<String,Long> checks=new LinkedHashMap<>();
        try(Connection c=connect(database);Statement s=c.createStatement()){
            var queries=new LinkedHashMap<String,String>();
            queries.put("restricoesUnicas", "SELECT count(*) FROM pg_constraint WHERE connamespace='public'::regnamespace AND conname IN ('uk_checkin_aluno_aula','uk_pagamento_contrato_parcela') AND contype='u'");
            queries.put("aulasAcimaDeOito", "SELECT count(*) FROM (SELECT aula_id FROM checkins WHERE status IN ('CONFIRMADO','PRESENTE') GROUP BY aula_id HAVING count(*)>8) q");
            queries.put("pacotesComCreditoExcedido", "SELECT count(*) FROM (SELECT c.id FROM contratos c JOIN checkins ch ON ch.contrato_id=c.id WHERE ch.status IN ('CONFIRMADO','PRESENTE','FALTA') GROUP BY c.id,c.aulas_contratadas HAVING count(*)>c.aulas_contratadas) q");
            queries.put("reservasDuplicadas", "SELECT count(*) FROM (SELECT aluno_id,aula_id FROM checkins GROUP BY aluno_id,aula_id HAVING count(*)>1) q");
            queries.put("parcelasDuplicadas", "SELECT count(*) FROM (SELECT contrato_id,numero_parcela FROM pagamentos GROUP BY contrato_id,numero_parcela HAVING count(*)>1) q");
            queries.put("tokensDeRecuperacaoSemHash", "SELECT count(*) FROM recuperacoes_senha WHERE token_hash !~ '^[0-9a-f]{64}$'");
            for(var e:queries.entrySet())try(var rs=s.executeQuery(e.getValue())){rs.next();checks.put(e.getKey(),rs.getLong(1));}
        }
        boolean ok=checks.get("restricoesUnicas")==2 && checks.entrySet().stream().filter(e->!e.getKey().equals("restricoesUnicas")).allMatch(e->e.getValue()==0);
        StringBuilder json=new StringBuilder("{\n  \"banco\": \"").append(database).append("\",\n  \"aprovado\": ").append(ok);
        for(var e:checks.entrySet())json.append(",\n  \"").append(e.getKey()).append("\": ").append(e.getValue());
        Files.writeString(reports.resolve("Verificacao-PostgreSQL.json"),json.append("\n}\n").toString(),StandardCharsets.UTF_8);
        System.out.println("Verificação direta PostgreSQL: "+checks);
        return ok;
    }
}
