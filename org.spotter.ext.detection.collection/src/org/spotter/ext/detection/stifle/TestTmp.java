package org.spotter.ext.detection.stifle;

import java.util.Random;
import java.util.UUID;

import org.lpe.common.util.LpeNumericUtils;
import org.lpe.common.util.LpeStringUtils;

public class TestTmp {
	static Random rand = new Random(System.currentTimeMillis());
public static void main(String[] args) {
	
	String sql1 ="SELECT a FROM "+getSubQuery(0, 4)+" WHERE fibo=25 AND a=1";
	System.out.println(sql1);
	String sql2 = "SELECT max(a) FROM a WHERE b=2";
	System.out.println(LpeStringUtils.areEqualSql(sql1, sql2));
}



private static  String getSubQuery(int count, int maxDepth) {
	int i = rand.nextInt(20);
	String [] strArray = {"a","b","c","d","e","f","g","h","j","i","k","l","m","n","o","p","q","r","s","t"};
	String s = strArray[rand.nextInt(strArray.length)];

	String randStr = "";
	int n = rand.nextInt(5);
	
	String uuid = getUniqueString(strArray);
	
	if (n < 1) {
		randStr = "(SELECT max(a) FROM A WHERE D = 2 ORDER BY x) as "  + uuid;
	} else if (n < 2) {
		randStr = "(SELECT max(a) FROM Y WHERE b = 2 GROUP BY y) as "+uuid;
	} else if (n < 3) {
		randStr = "(SELECT max(a) FROM X WHERE V = 2 GROUP BY x) as "+ uuid;
	} else if (n < 4) {
		randStr = "(SELECT y FROM Y WHERE X = 2 ORDER BY x) as "+ uuid;
	} else if (n < 5) {
		randStr = "(SELECT b FROM X WHERE Y = 2 GROUP BY x) as "+ uuid;
	}

	if (count < maxDepth) {
		count++;
		return "(SELECT a FROM " + randStr + " , " + getSubQuery(count, maxDepth) + " WHERE " + s + "=" + i + ") as "+ getUniqueString(strArray);
	} else {
		return randStr;
	}
}



private static String getUniqueString(String[] strArray) {
	String uuid = "";
	String str = UUID.randomUUID().toString().replace("-", "");
	for(int a = 0; a < str.length(); a++){
		Character c = str.charAt(a);
		
		if(Character.isDigit(c)){
			uuid += strArray[rand.nextInt(20)];
		}else{
			uuid += c;
		}
	}
	return uuid;
}
}
