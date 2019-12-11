INSERT INTO test (id,name,description,schema) VALUES (1,'SPECjEnterprise2010','spec.org 2010 enterprise benchmark',null);

INSERT INTO hook (id,url,type,target,active) VALUES (  0,'http://laptop:8080/api/log','new/test',  1,true);
INSERT INTO hook (id,url,type,target,active) VALUES (  1,'http://laptop:8080/api/log','new/test', -1,false);