package lab.sql.jdbc.mysql.parallelism

/**
 * 
 1. MySQL 설치 on CentOS7....
  - # yum install -y http://dev.mysql.com/get/mysql-community-release-el7-5.noarch.rpm
  - # yum install -y mysql-community-server
  - # systemctl start mysqld
  - # systemctl status mysqld.service
  - # systemctl enable mysqld
 
 
 2. 원격접속 허용(root계정 모든 IP 허용).... + Table 생성 및 Data 추가....
  - # cd /usr/bin //--불필요....
  - # mysql
  - mysql> SELECT Host,User,Password FROM mysql.user;
  - mysql> INSERT INTO mysql.user (host, user, password, ssl_cipher, x509_issuer, x509_subject) VALUES ('%','root',password(''),'','','');
  - mysql> GRANT ALL PRIVILEGES ON *.* TO 'root'@'%';
  - mysql> FLUSH PRIVILEGES;
  - mysql> show databases;
  - mysql> use mysql;
  - mysql> DROP TABLE IF EXISTS projects_parallelism;
  - mysql> 
CREATE TABLE projects_parallelism (
  id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  name varchar(255),
  website varchar(255),
  manager varchar(255),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
  - mysql> describe projects_parallelism;
  - mysql> truncate projects_parallelism;
  - mysql> 
INSERT INTO projects_parallelism (name, website, manager) VALUES ('Apache Spark', 'http://spark.apache.org', 'Michael');
INSERT INTO projects_parallelism (name, website, manager) VALUES ('Apache Hive', 'http://hive.apache.org', 'Andy');
INSERT INTO projects_parallelism VALUES (DEFAULT, 'Apache Kafka', 'http://kafka.apache.org', 'Justin');
INSERT INTO projects_parallelism VALUES (DEFAULT, 'Apache Flink', 'http://flink.apache.org', 'Michael');
	- mysql> 
INSERT INTO projects_parallelism (name, website, manager) SELECT name, website, manager FROM projects_parallelism;
INSERT INTO projects_parallelism (name, website, manager) SELECT name, website, manager FROM projects_parallelism;
INSERT INTO projects_parallelism (name, website, manager) SELECT name, website, manager FROM projects_parallelism;
INSERT INTO projects_parallelism (name, website, manager) SELECT name, website, manager FROM projects_parallelism;
  - mysql> SELECT count(*), min(id), max(id) FROM projects_parallelism;
+----------+---------+---------+
| count(*) | min(id) | max(id) |
+----------+---------+---------+
|       64 |       1 |      89 |
+----------+---------+---------+
  - mysql> SELECT * FROM projects_parallelism;


 3. MySQL JDBC Driver download.... + upload....
  - https://dev.mysql.com/downloads/connector/j/
  - Platform Independent (Architecture Independent), ZIP Archive > Download > No thanks, just start my download.
  - unzip mysql-connector-java-5.1.39.zip
  - upload mysql-connector-java-5.1.39-bin.jar to CentOS7-14:/kikang/spark-2.3.1-bin-hadoop2.7
 
 
 4. MySQL SQL Query Log Check....
  - mysql> show variables like 'general%';
+------------------+-------------------------------+
| Variable_name    | Value                         |
+------------------+-------------------------------+
| general_log      | OFF                           |
| general_log_file | /var/lib/mysql/CentOS7-14.log |
+------------------+-------------------------------+

  - mysql> set global general_log=on;
  
  - mysql> show variables like 'general%';
+------------------+-------------------------------+
| Variable_name    | Value                         |
+------------------+-------------------------------+
| general_log      | ON                            |
| general_log_file | /var/lib/mysql/CentOS7-14.log |
+------------------+-------------------------------+

  - # tail -f /var/lib/mysql/CentOS7-14.log		//--tail로 바로 sql 확인....
  - # vi /var/lib/mysql/CentOS7-14.log				//--필요시 vi로 log 파일 확인....
  
  - mysql> SELECT count(*), min(id), max(id) FROM projects_parallelism;
  - tail을 통해 SQL Log 확인....


 5. Spark Shell 연결.... => http://spark.apache.org/docs/latest/sql-programming-guide.html#jdbc-to-other-databases
  - # ./bin/spark-shell --jars mysql-connector-java-5.1.39-bin.jar --master spark://CentOS7-14:7077	 //=> executor 1개 구동....
  - # ./bin/spark-shell --jars mysql-connector-java-5.1.39-bin.jar --master spark://CentOS7-14:7077 --executor-memory 992m --executor-cores 1 --total-executor-cores 2 --name kikang_spark_shell4 //=> executor 2개 구동.... + executor 별 memory, core 지정.... + App Name 지정....
  - scala> val df_parallelism = spark.
  read.
  format("jdbc").
  option("url", "jdbc:mysql://CentOS7-14:3306/mysql").
  option("driver", "com.mysql.jdbc.Driver").
  option("dbtable", "projects_parallelism").
  option("user", "root").
  option("password", "").
  option("numPartitions", 5).
  option("partitionColumn", "id").
  option("lowerBound", 10).
  option("upperBound", 80).
  load()	//=> spark_home/work/app_home/executor_id 경로내 JDBC 드라이버 jar 파일 존재여부 확인....아직 executor에서는 JDBC 드라이버 jar 파일이 필요없음....
  - scala> df_parallelism.printSchema	//=> "jdbc" 포맷으로 읽어온 DataFrame의 스키마정보는 Spark Driver에서 읽어온 것임....
  - scala> df_parallelism.show(false)	//=> spark_home/work/app_home/executor_id 경로내 JDBC 드라이버 jar 파일 존재여부 확인....실제 구동(Action 실행)될 때 필요한 jar 파일 가져옴....각 executor에서 DB로부터 데이터를 읽어오기 위해 JDBC 드라이버 jar 파일을 Spark Driver로부터 가져옴....  
  - scala> df_parallelism.show(5, false)					//--Driver UI > SQL > Completed Queries > Description 링크 > SQL DAG 내 number of output rows 개수 + numPartitions 값 확인 > Succeeded Jobs의 실제 Task 개수 확인....
  - scala> df_parallelism.show(30, false)					//--Driver UI > SQL > Completed Queries > Description 링크 > SQL DAG 내 number of output rows 개수 + numPartitions 값 확인 > Succeeded Jobs의 실제 Task 개수 확인....
  - scala> df_parallelism.show(50, false)					//--Driver UI > SQL > Completed Queries > Description 링크 > SQL DAG 내 number of output rows 개수 + numPartitions 값 확인 > Succeeded Jobs의 실제 Task 개수 확인....
  - scala> df_parallelism.show(500, false)				//--Driver UI > SQL > Completed Queries > Description 링크 > SQL DAG 내 number of output rows 개수 + numPartitions 값 확인 > Succeeded Jobs의 실제 Task 개수 확인....
  - scala> df_parallelism.count()									//--Driver UI > SQL > Completed Queries > Description 링크 > SQL DAG 내 number of output rows 개수 + numPartitions 값 확인 > Succeeded Jobs의 실제 Task 개수 확인....
  
  
  6. Parallelism on READ....   
  - scala> df_parallelism.show(500, false)				//--쿼리 5개????   
  - scala> df_parallelism.show(5, false)					//--쿼리 1개????  
  - scala> df_parallelism.count()									//--쿼리 5개????   
  - scala> val df_no_parallelism = spark.
  read.
  format("jdbc").
  option("url", "jdbc:mysql://CentOS7-14:3306/mysql").
  option("driver", "com.mysql.jdbc.Driver").
  option("dbtable", "projects_parallelism").
  option("user", "root").
  option("password", "").
  //option("numPartitions", 5).
  //option("partitionColumn", "id").
  //option("lowerBound", 10).
  //option("upperBound", 8).
  load()
  - scala> df_no_parallelism.printSchema	//=> "jdbc" 포맷으로 읽어온 DataFrame의 스키마정보는 Spark Driver에서 읽어온 것임....  
  - scala> df_no_parallelism.show(500, false)			//--쿼리 1개????		//--Driver UI > SQL > Completed Queries > Description 링크 > SQL DAG 내 number of output rows 개수 확인 > Succeeded Jobs의 실제 Task 개수 확인....
  - scala> df_no_parallelism.show(5, false)				//--쿼리 1개????  
  - scala> df_no_parallelism.count()							//--쿼리 1개???? 


 7. Parallelism on WRITE....   
  - scala> val prop: java.util.Properties = new java.util.Properties()
  - scala> prop.setProperty("user", "root")
  - scala> prop.setProperty("password", "")
  - scala> prop.setProperty("driver", "com.mysql.jdbc.Driver")
    
    
  - #01. Parallel READ + Parallel WRITE.... (is OK????)	=> Task 개수 '5'.... READ 쿼리 및 WRITE 실행 각각 5개 병렬 처리.... => WRITE에 의해 입력된 값이 다른 Task의 READ 쿼리에 조회되어 원치 않는 결과 발생????
  - scala> df_parallelism.rdd.partitions.length
  - scala> df_parallelism.rdd.getNumPartitions
  
  - //--SaveMode.Append => 기존 테이블에 Data Insert....
  - mysql> SELECT count(*), min(id), max(id) FROM projects_parallelism;
+----------+---------+---------+
| count(*) | min(id) | max(id) |
+----------+---------+---------+
|       64 |       1 |      89 |
+----------+---------+---------+
  - scala> df_parallelism.select("name", "website", "manager").write.mode(org.apache.spark.sql.SaveMode.Append).jdbc("jdbc:mysql://CentOS7-14:3306/mysql", "projects_parallelism", prop)
  - Driver UI > SQL > Completed Queries > Description 링크 > Succeeded Jobs의 실제 Task 개수 확인....
  - tail을 통해 SQL Log 확인.... 
  - mysql> SELECT count(*), min(id), max(id) FROM projects_parallelism;		//--개수가 이상함????
+----------+---------+---------+
| count(*) | min(id) | max(id) |
+----------+---------+---------+
|      160 |       1 |     216 |
+----------+---------+---------+
  
  
  - #02. Single READ + Single WRITE.... (OK!!!!) => Task 개수 '1'....
  - scala> df_no_parallelism.rdd.partitions.length
  - scala> df_no_parallelism.rdd.getNumPartitions
  
  - //--SaveMode.Append => 기존 테이블에 Data Insert....
  - mysql> SELECT count(*), min(id), max(id) FROM projects_parallelism;
+----------+---------+---------+
| count(*) | min(id) | max(id) |
+----------+---------+---------+
|      160 |       1 |     216 |
+----------+---------+---------+
  - scala> df_no_parallelism.select("name", "website", "manager").write.mode(org.apache.spark.sql.SaveMode.Append).jdbc("jdbc:mysql://CentOS7-14:3306/mysql", "projects_parallelism", prop)
  - Driver UI > SQL > Completed Queries > Description 링크 > Succeeded Jobs의 실제 Task 개수 확인....
  - tail을 통해 SQL Log 확인.... 
  - mysql> SELECT count(*), min(id), max(id) FROM projects_parallelism;		//--개수 OK!!!!
+----------+---------+---------+
| count(*) | min(id) | max(id) |
+----------+---------+---------+
|      320 |       1 |     376 |
+----------+---------+---------+
  
  
  - #03. Single READ + Parallel WRITE.... (OK!!!!) => Task 개수 '1' + '3'....
  - scala> val df_no_parallelism_03 = df_no_parallelism.repartition(3)
  - scala> df_no_parallelism_03.rdd.partitions.length
  - scala> df_no_parallelism_03.rdd.getNumPartitions
  
  - //--SaveMode.Append => 기존 테이블에 Data Insert....
  - mysql> SELECT count(*), min(id), max(id) FROM projects_parallelism;
+----------+---------+---------+
| count(*) | min(id) | max(id) |
+----------+---------+---------+
|      320 |       1 |     376 |
+----------+---------+---------+
  - scala> df_no_parallelism_03.select("name", "website", "manager").write.mode(org.apache.spark.sql.SaveMode.Append).jdbc("jdbc:mysql://CentOS7-14:3306/mysql", "projects_parallelism", prop)
  - Driver UI > SQL > Completed Queries > Description 링크 > Succeeded Jobs의 실제 Task 개수 확인....
  - tail을 통해 SQL Log 확인.... 
  - mysql> SELECT count(*), min(id), max(id) FROM projects_parallelism;		//--개수 OK!!!!
+----------+---------+---------+
| count(*) | min(id) | max(id) |
+----------+---------+---------+
|      640 |       1 |     696 |
+----------+---------+---------+
  
  
  - #04. Parallel READ + Single WRITE(by 'coalesce').... (OK!!!!)	=> Task 개수 '1'.... READ 쿼리 5개 순차 처리 후 WRITE 실행....
  - scala> val df_parallelism_01 = df_parallelism.coalesce(1)		//--by coalesce()....
  - scala> df_parallelism_01.rdd.partitions.length
  - scala> df_parallelism_01.rdd.getNumPartitions
  
  - //--SaveMode.Append => 기존 테이블에 Data Insert....
  - mysql> SELECT count(*), min(id), max(id) FROM projects_parallelism;
+----------+---------+---------+
| count(*) | min(id) | max(id) |
+----------+---------+---------+
|      640 |       1 |     696 |
+----------+---------+---------+
  - scala> df_parallelism_01.select("name", "website", "manager").write.mode(org.apache.spark.sql.SaveMode.Append).jdbc("jdbc:mysql://CentOS7-14:3306/mysql", "projects_parallelism", prop)
  - Driver UI > SQL > Completed Queries > Description 링크 > Succeeded Jobs의 실제 Task 개수 확인....
  - tail을 통해 SQL Log 확인.... 
  - mysql> SELECT count(*), min(id), max(id) FROM projects_parallelism;		//--개수 OK!!!!
+----------+---------+---------+
| count(*) | min(id) | max(id) |
+----------+---------+---------+
|     1280 |       1 |    1336 |
+----------+---------+---------+
  
  
  - #05. Parallel READ + Single WRITE(by 'repartition').... (OK!!!!)	=> Task 개수 '5' + '1'.... READ 쿼리 5개 병렬 처리 후 WRITE 실행....
  - scala> val df_parallelism_001 = df_parallelism.repartition(1)		//--by repartition()....
  - scala> df_parallelism_001.rdd.partitions.length
  - scala> df_parallelism_001.rdd.getNumPartitions
  
  - //--SaveMode.Append => 기존 테이블에 Data Insert....
  - mysql> SELECT count(*), min(id), max(id) FROM projects_parallelism;
+----------+---------+---------+
| count(*) | min(id) | max(id) |
+----------+---------+---------+
|     1280 |       1 |    1336 |
+----------+---------+---------+
  - scala> df_parallelism_001.select("name", "website", "manager").write.mode(org.apache.spark.sql.SaveMode.Append).jdbc("jdbc:mysql://CentOS7-14:3306/mysql", "projects_parallelism", prop)
  - Driver UI > SQL > Completed Queries > Description 링크 > Succeeded Jobs의 실제 Task 개수 확인....
  - tail을 통해 SQL Log 확인.... 
  - mysql> SELECT count(*), min(id), max(id) FROM projects_parallelism;		//--개수 OK!!!!
+----------+---------+---------+
| count(*) | min(id) | max(id) |
+----------+---------+---------+
|     2560 |       1 |    2616 |
+----------+---------+---------+


  - #06. Parallel READ + Single WRITE(by 'coalesce').... => coalesce(3)이라면????
  
  
 * 
 */
object SparkShell {
  
}


 - # mysql 
     => 'root' 계정에 패스워드 'root' 설정....
     
  - mysql> use mysql;
  
  - mysql> SELECT Host,User,Password FROM mysql.user;
  
  - mysql> update user set password=PASSWORD('root') where user='root';  
  
  - mysql> flush privileges;  
  
  - mysql> grant all on *.* to 'root'@'localhost' identified by 'root' with grant option;
  
  - mysql> grant all on *.* to 'root'@'CentOS7-14' identified by 'root' with grant option;
  
  - mysql> grant all on *.* to 'root'@'%' identified by 'root' with grant option;
  
  - mysql> flush privileges;
  
  - mysql> quit;
  
  - # mysqladmin -u root -p shutdown
  
  - # systemctl start mysqld	=> centos6의 경우 # service mysqld start
     
     
     
  - # mysql -uroot -proot
     => 기존에 'metastore_db' 데이터베이스가 있으면 삭제.... 
  
  - mysql> drop database if exists metastore_db;
  
  - mysql> show databases;
  
