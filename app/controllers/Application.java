package controllers;


import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import ca.usask.cs.srlab.simcad.model.CloneFragment;
import models.Clone;
import play.mvc.*;
import views.html.*;

public class Application extends Controller {
	
	private static Clone clone = null;

    public static Result index() throws Exception {
    	if(clone == null) {
    		clone = new Clone();
    	}
    	
        return ok(index.render(clone));
    }
    
    public static Result detail() throws Exception {
    	
    	if(clone == null) {
    		clone = new Clone();
    	}
    	
        return ok(detail.render(clone));
    }
    
    public static Result detailC2M() throws Exception {
    	if(clone == null) {
    		clone = new Clone();
    	}
        return ok(detailC2M.render(clone));
    }
    
    public static Result macroFragments() throws Exception {
    	if(clone == null) {
    		clone = new Clone();
    	}
    	List<CloneFragment> fragments = clone.loadMacroFragements();
    	
    	// Java8
//    	Collections.sort(fragments, (f1, f2) -> Integer.compare(f1.getFromLine(), f2.getFromLine()));
    	
    	// Java 7
		Collections.sort(fragments, new Comparator<CloneFragment>() {
			@Override
			public int compare(CloneFragment f1, CloneFragment f2) {
				
				int key1 = f1.getFileName().compareTo(f2.getFileName());
				
				if(key1 != 0)
				{
					return key1;
				} else {
					return Integer.compare(f1.getFromLine(), f2.getFromLine());
				}
			}
		});
		
		
		Map<String,Integer> groups = new TreeMap<String,Integer>();
		
		for(CloneFragment fragment : fragments)
		{
			String key = fragment.getFileName();
			if(!groups.containsKey(key)) {
				groups.put(key, 0);
			}
			groups.put(key, groups.get(key) + 1);
		}
    	
        return ok(fragmentList.render(fragments, groups, clone));
    }
    
    public static Result cobolFragments() throws Exception {
    	if(clone == null) {
    		clone = new Clone();
    	}
    	List<CloneFragment> fragments = clone.loadCobolFragements();
//    	Collections.sort(fragments, (f1, f2) -> Integer.compare(f1.getFromLine(), f2.getFromLine()));
		Collections.sort(fragments, new Comparator<CloneFragment>() {
			@Override
			public int compare(CloneFragment f1, CloneFragment f2) {
				int key1 = f1.getFileName().compareTo(f2.getFileName());
				
				if(key1 != 0)
				{
					return key1;
				} else {
					return Integer.compare(f1.getFromLine(), f2.getFromLine());
				}
			}
		});
		
		Map<String,Integer> groups = new TreeMap<String,Integer>();
		
		for(CloneFragment fragment : fragments)
		{
			String key = fragment.getFileName();
			if(!groups.containsKey(key)) {
				groups.put(key, 0);
			}
			groups.put(key, groups.get(key) + 1);
		}
		
        return ok(fragmentList.render(fragments, groups, clone));
    }
    
    public static Result statistics() throws Exception {
    	if(clone == null) {
    		clone = new Clone();
    	}
    	
        return ok(statistics.render(clone));
    }
    
}
