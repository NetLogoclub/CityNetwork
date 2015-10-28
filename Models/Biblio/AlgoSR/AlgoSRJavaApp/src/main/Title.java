/**
 * 
 */
package main;

/**
 * @author Raimbault Juste <br/> <a href="mailto:juste.raimbault@polytechnique.edu">juste.raimbault@polytechnique.edu</a>
 *
 */
public class Title {
	
	/**
	 * Empty title.
	 */
	public static final Title EMPTY = new Title("");
	
	/**
	 * title in itself
	 */
	public String title;
	
	/**
	 * english title
	 */
	public String en_title;
	
	
	public String language;
	
	/**
	 * if title has been translated : (en_title != "")
	 */
	public boolean translated;
	
	/**
	 * 
	 */
	public Title(String t){
		title = t;
	}
	
	public Title(String t,String e){
		title = t;
		en_title = e;
		translated = (en_title != "");
	}
	
	public Title(String t,String e,String l){
		title = t;
		en_title = e;
		language = l;
		translated = (en_title != "");
	}
	
	
	@Override
	public String toString(){
		return title;
	}
	
	
	
	
}
