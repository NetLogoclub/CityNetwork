/**
 * 
 */
package sql;

import main.reference.Abstract;
import main.reference.CybergeoBiblioParser;
import main.reference.Reference;
import main.reference.Title;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import utils.BasicWriter;
import utils.CSVWriter;
import utils.GEXFWriter;
import utils.RISWriter;
import utils.StringUtils;

/**
 * @author Raimbault Juste <br/> <a href="mailto:juste.raimbault@polytechnique.edu">juste.raimbault@polytechnique.edu</a>
 *
 */
public class CybergeoImport {

	
	
	
	/**
	 * Imports the SQL base as a set of References.
	 * 
	 * @param filter : additional SQL request filter
	 *   	"	WHERE  `datepubli` >=  '2003-01-01' AND  `resume` !=  '' AND  `titre` != ''   "
	 *      append to the basic sql request.
	 *      
	 * @return Set of refs
	 */
	public static HashSet<Reference> importBase(String filter){
		
		HashSet<Reference> res = new HashSet<Reference>();
		
		try{
		   ResultSet sqlrefs = SQLConnection.sqlDB.createStatement().executeQuery(
				"SELECT `titre`,`altertitre`,`resume`,`datepubli`,`identity`,`langue`,`bibliographie` "
				+ "FROM  `textes` "
				+ filter +";");
		   while(sqlrefs.next()){
			   
			   String id = sqlrefs.getString(5);
			   String biblio = sqlrefs.getString(7);
			   Title title = getTitle(sqlrefs.getString(1),sqlrefs.getString(2),sqlrefs.getString(6));
			   Reference r = Reference.construct(id,title, getAbstract(sqlrefs.getString(3)), sqlrefs.getString(4), "");
				
			   r.date= sqlrefs.getString(4);
			   r.addAttribute("langue", sqlrefs.getString(6));
			   r.addAttribute("translated",new Boolean(title.translated).toString());
			   
			   // get authors
			   ResultSet authorsIds = SQLConnection.sqlDB.createStatement().executeQuery("SELECT `id2` FROM  `relations` WHERE  `id1` = " +sqlrefs.getString(5)+" AND  `nature` LIKE  'G' ORDER BY  `degree` ASC ;");
			   while(authorsIds.next()){
			      ResultSet author = SQLConnection.sqlDB.createStatement().executeQuery("SELECT `nomfamille`,`prenom` FROM `auteurs` WHERE `idperson` = "+authorsIds.getString(1)+" ;");
			      if(author.next()){r.authors.add(author.getString(2)+" , "+author.getString(1));}
			   }
			   
			   // get keywords
			   ResultSet keywordsIds = SQLConnection.sqlDB.createStatement().executeQuery("SELECT `id2` FROM  `relations` WHERE  `id1` = " +sqlrefs.getString(5)+" AND  `nature` LIKE  'E' ORDER BY  `degree` ASC ;");
			   while(keywordsIds.next()){
			      ResultSet keywords = SQLConnection.sqlDB.createStatement().executeQuery("SELECT `nom` FROM `indexes` WHERE `identry` = "+keywordsIds.getString(1)+" ;");
			      while(keywords.next()){r.keywords.add(keywords.getString(1));}
			   }
			   
			// keywords fr
			   String keywords_fr = "";
			   ResultSet kwFRIds = SQLConnection.sqlDB.createStatement().executeQuery("SELECT `id2` FROM  `relations` WHERE  `id1` = " +id+" AND  `nature` LIKE  'E' ORDER BY  `degree` ASC ;");
			   while(kwFRIds.next()){
			      ResultSet kw = SQLConnection.sqlDB.createStatement().executeQuery("SELECT `g_name` FROM `entries` WHERE `id` = "+kwFRIds.getString(1)+" AND `idtype`=33 ;");
			      while(kw.next()){if(keywords_fr.length()>0){keywords_fr+=",";}keywords_fr+=kw.getString(1);}
			   }
			   r.addAttribute("keywords_fr", keywords_fr);
			   
			   
			   r.biblio = new CybergeoBiblioParser().parse(biblio);
			   
			   //if(r.authors.size()>0){res.add(r);}
			   //else{System.out.println(r.toString());}
			   //if(r.title.title.length()<5){System.out.println(r);}
			   
			   res.add(r);
			   
		   }
		   
		}catch(Exception e){
			e.printStackTrace();
		}
		
		return res;
	}
	
	
	
	/**
	 * Performs directly a csv + raw texts and abstracts export of the all base.
	 * (to not touch csv anymore)
	 * 
	 * @param outDir
	 */
	public static void directExport(String outDir,boolean writeFullTexts){
		LinkedList<String[]> table = new LinkedList<String[]>();
		
		try{
			// add header
			   String[] header = {"id","Title","Title_en","keywords_en","keywords_fr","authors","date","langue","translated"};
			   table.add(header);
			
			   ResultSet sqlrefs = SQLConnection.sqlDB.createStatement().executeQuery(
					"SELECT `identity`,`titre`,`altertitre`,`langue`,`datepubli` "
					+ "FROM  `textes`;");
			   			   
			   while(sqlrefs.next()){
				   String id = sqlrefs.getString(1);
				   String lang = sqlrefs.getString(4);
				   Title proc_title = getTitle(sqlrefs.getString(2),sqlrefs.getString(3),lang);
				   String title = proc_title.title;
				   String title_en = proc_title.en_title;
				   
				   String date = sqlrefs.getString(5);
				   boolean translated = proc_title.translated;
				   
				   String[] row = {id,title,title_en,"","","",date,lang,new Boolean(translated).toString()};
				   
				   // get authors
				   String authors = "";
				   ResultSet authorsIds = SQLConnection.sqlDB.createStatement().executeQuery("SELECT `id2` FROM  `relations` WHERE  `id1` = " +id+" AND  `nature` LIKE  'G' ORDER BY  `degree` ASC ;");
				   while(authorsIds.next()){
				      ResultSet author = SQLConnection.sqlDB.createStatement().executeQuery("SELECT `nomfamille`,`prenom` FROM `auteurs` WHERE `idperson` = "+authorsIds.getString(1)+" ;");
				      if(author.next()){if(authors.length()>0){authors+=",";}authors+=author.getString(2)+" "+author.getString(1);}
				   }
				   row[5]=authors;
				   
				   // get keywords
				   
				   /**
				    * entrytypes : motsclesen : id=34
				    * 			   motsclesfr : id=33
				    * 			      - de : id=5712
				    * ... -> get only en keywords ?
				    */
				   String keywords = "";
				   ResultSet kwIds = SQLConnection.sqlDB.createStatement().executeQuery("SELECT `id2` FROM  `relations` WHERE  `id1` = " +id+" AND  `nature` LIKE  'E' ORDER BY  `degree` ASC ;");
				   while(kwIds.next()){
				      ResultSet kw = SQLConnection.sqlDB.createStatement().executeQuery("SELECT `g_name` FROM `entries` WHERE `id` = "+kwIds.getString(1)+" AND `idtype`=34 ;");
				      while(kw.next()){if(keywords.length()>0){keywords+=",";}keywords+=kw.getString(1);}
				   }
				   //if(keywords.length()==0){keywords="-";}
				   row[3]=keywords;
				   
				   // keywords fr
				   String keywords_fr = "";
				   ResultSet kwFRIds = SQLConnection.sqlDB.createStatement().executeQuery("SELECT `id2` FROM  `relations` WHERE  `id1` = " +id+" AND  `nature` LIKE  'E' ORDER BY  `degree` ASC ;");
				   while(kwFRIds.next()){
				      ResultSet kw = SQLConnection.sqlDB.createStatement().executeQuery("SELECT `g_name` FROM `entries` WHERE `id` = "+kwFRIds.getString(1)+" AND `idtype`=33 ;");
				      while(kw.next()){if(keywords_fr.length()>0){keywords_fr+=",";}keywords_fr+=kw.getString(1);}
				   }
				   //if(keywords_fr.length()==0){keywords_fr="-";}
				   row[4]=keywords_fr;
				   
				   if(writeFullTexts){
					   new File(outDir+"/texts").mkdir();
					   // print text and abstract in files
					   ResultSet rawText = SQLConnection.sqlDB.createStatement().executeQuery("SELECT `texte` FROM textes WHERE `identity` = "+id+" LIMIT 1 ; ");
					   if(rawText.next()){
						   BasicWriter.write(outDir+"/texts/"+id+"_text.txt", rawText(rawText.getString(1)));
					   }

					   ResultSet res = SQLConnection.sqlDB.createStatement().executeQuery("SELECT `resume` FROM textes WHERE `identity` = "+id+" LIMIT 1 ; ");
					   if(res.next()){
						   LinkedList<String> t = new LinkedList<String>();
						   t.add(getAbstract(res.getString(1)).resume);
						   BasicWriter.write(outDir+"/texts/"+id+"_abstract.txt", t);
					   }
				   }
				   
				   System.out.println(title);
				   
				   table.add(row);
				   
			   }
			   
			   // export to csv file
			   String[][] csv = new String[table.size()][table.get(0).length];
			   for(int i=0;i<csv.length;i++){csv[i]=table.get(i);}
			   
			   CSVWriter.write(outDir+"cybergeo_withFR.csv", csv, "\t","\"");
			   
			}catch(Exception e){
				e.printStackTrace();
			}
			
		
	}
	

	
	
	
	/**
	 * Get english raw text from xml multiling structure
	 * 
	 * @param xml
	 * @return
	 */
	public static Abstract getAbstract(String xml){
		Document d = Jsoup.parse(xml);
		try{
		return new Abstract(d.getElementsByAttributeValue("lang", "en").first().text());
		}catch(Exception e){return new Abstract(xml);}
	}
	
	/**
	 * 
	 * 
	 * @param html
	 * @return
	 */
	public static LinkedList<String> rawText(String html){
		LinkedList<String> t = new LinkedList<String>();
		Document d = Jsoup.parse(html);
		try{
		  for(Element e:d.getElementsByTag("p")){t.add(e.text());}
		}catch(Exception e){t.add(html);}
		return t;
	}
	
	
	/**
	 * Get eng title from title, altertitle fields
	 * 
	 * @param title
	 * @param altertitle
	 * @return
	 */
	public static Title getTitle(String title,String altertitle,String lang){
		// find english title : if not in altertitle, then must be the main title
		Document ad = Jsoup.parse(altertitle);
		Document d = Jsoup.parse(title);
		//return d.text();
		
		String restitle="",resentitle="";


		// original language is english
		// no need to fill en_title field
		try{
			restitle = d.getElementsByAttributeValue("lang", lang).first().text();				
		}catch(Exception e){
			try{restitle = d.getElementsByTag("span").first().text();}catch(Exception ee){restitle=title;}
		}
		
		if(lang.compareTo("en")!=0){
			//other language or not filled
			try{
				resentitle = d.getElementsByAttributeValue("lang", "en").first().text();
			}catch(Exception e){
				try{
					resentitle =  ad.getElementsByAttributeValue("lang", "en").first().text();
				}catch(Exception ee){}
			}
		}
		
		//if(restitle.length()==0){restitle="";}
		//if(resentitle.length()==0){resentitle="";}
		if(restitle.length()==0&&resentitle.length()>0){restitle=resentitle;}
		
		if(restitle.replace(" ", "").length()<4){
			//System.out.println("ERROR : "+title);
			//System.out.println(d.text());
			//System.out.println();
			restitle = d.text();
			//System.out.println(restitle.replace("\"", ""));
	    }
		
		return new Title(restitle.replace("\"", ""),resentitle.replace("\"", ""),title,lang);
	}
	
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		SQLConnection.setupSQLCredentials("root", "root");
		SQLConnection.setupSQL("Cybergeo");
		//GEXFWriter.write("res/test_cyb_gexf.gexf", importBase());
		//RISWriter.write("/Users/Juste/Documents/ComplexSystems/Cybergeo/Data/processed/2003_fullbase_rawTitle_withKeywords.ris", importBase());
		//directExport(System.getenv("CS_HOME")+"/CyberGeo/cybergeo20/Data/raw/",false);
		
		HashSet<Reference> res = importBase("");//importBase("WHERE  `datepubli` >=  '2003-01-01' LIMIT 10");
		System.out.println(res.size());
		
		
		
	}

}
