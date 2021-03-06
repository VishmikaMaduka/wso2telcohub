var s = document.createElement("script");
s.type = "text/javascript";
s.src = "../themes/default/subthemes/dark/templates/subscription-task/js/bootbox.js";
$("head").append(s);

$(document).ready(function(){
    $('.js_startBtn').click(function(){
        var btn = $(this);
        var taskId=btn.attr("data");
        var iteration=btn.attr("iteration");
        btn.attr("disabled","disabled");
        jagg.post("/site/blocks/task-manager/ajax/task.jag", { action:"startTask",taskId:taskId,taskType:"subscription" },
            function (json) {
                if (!json.error) {
                    btn.next().show();
                    $('#js_completeBtn'+iteration).show();
                    btn.hide();
                    $('#status'+iteration).text("IN_PROGRESS");
                } else {
                jagg.showLogin();
                }
            }, "json");

        // Display the list of tiers for the task.
        //displayTiers(taskId);
        
    }).removeAttr("disabled","disabled");

    $('.js_completeBtn').click(function(){
        var btn = $(this);
        var taskId=btn.attr("data");
        var iteration=btn.attr("iteration");
        var description=$('#desc'+iteration).text();
        var status=$('.js_stateDropDown').val();
        var tierId = $("#tiers_list_" + taskId).val();
        
		bootbox.prompt("Enter approve/reject reasons", function(result) {  
              
      	 if (result === null) { 

	     }else{
		   btn.attr("disabled","disabled");
	
        
        jagg.post("/site/blocks/task-manager/ajax/task.jag?comment="+ result, { action:"completeTask",status:status,taskId:taskId,taskType:"subscription",description:description,selectedTier:tierId },
            function (json) {
                if (!json.error) {
                    btn.next().show();
                    btn.next().next().html(json.msg);
                    btn.hide();
                    window.location.reload();
                } else {
                    jagg.showLogin();
                }
            }, "json");
        
        // Sets the selected tier for the current approved subscription.
        if(status == "APPROVED") {
            setSubscriptionTier(taskId, tierId);
        }
		 }
		 
		 });

    }).removeAttr("disabled","disabled");

    $('.js_assignBtn').click(function(){
        var btn = $(this);
        var taskId=btn.attr("data");
        var iteration=btn.attr("iteration");
        btn.attr("disabled","disabled");
        jagg.post("/site/blocks/task-manager/ajax/task.jag", { action:"assignTask",taskId:taskId,taskType:"subscription" },
            function (json) {
                if (!json.error) {
                    btn.next().show();
                    $('#js_startBtn'+iteration).show();
                    btn.hide();
                    $('#status'+iteration).text("RESERVED");
                } else {
                    jagg.showLogin();
                }
            }, "json");
    }).removeAttr("disabled","disabled");
    
    $('.js_toggle').click(function(){
        var $i = $('i',this);
        if($i.hasClass('icon-chevron-right')){
            $(this).next().show();
            $i.removeClass('icon-chevron-right');
            $i.addClass('icon-chevron-down');
            $.cookie($(this).attr('data-section'),'show');
        }else{
            $(this).next().hide();
            $i.removeClass('icon-chevron-down');
            $i.addClass('icon-chevron-right');
            $.cookie($(this).attr('data-section'),'hide');
        }
        var taskId = $(this).attr('id');
        $('tiersDiv_' + taskId).show();
        //console.log($('tiersDiv_' + taskId));
        
    });
    
    $('.js_toggle').each(function(){
        if($.cookie($(this).attr('data-section'))=="hide"){
            var $i = $('i',this);
            $(this).next().hide();
            $i.removeClass('icon-chevron-down');
            $i.addClass('icon-chevron-right');
        }
    });
    
    // Loads and displays the tiers for IN_PROGRESS tasks in the current page.
    //displayTiersForInProgresstasks();
    
    loadAllTaskTierDetails();
});

//Displays the list of tiers for the specified task.
function displayTiers(taskId) {

	jagg.post("/site/blocks/subscription-task/ajax/subscription-task.jag", { action:"getSubscriptionTiers",taskId:taskId },
		function (json) {
    		if (!json.error) {
    			var tiers = json.tiers;
    			var tiersList = document.getElementById('tiers_list_' + taskId);

    			$.each(tiers, function(key, tier) {
    				if(tier.name.toUpperCase() != "DEFAULT") {
    					tiersList.options[tiersList.options.length] = new Option(tier.displayName, tier.name);
    				}
    			})
    			
    		} else {
                jagg.showLogin();
    		}
    	}, "json");
    
	document.getElementById('tiersDiv_' + taskId).style.display = 'block';
}

// Loads the tiers for IN_PROGRESS tasks.
function displayTiersForInProgresstasks() {
	
	var inProgressTasks = document.getElementsByName("in_progress_task");
	
	for(var i =0; i < inProgressTasks.length; i++) {
		var taskId = inProgressTasks[i].value;
		displayTiers(taskId);
	}
}

// Sets the selected tier for the current completed subscription.
function setSubscriptionTier(taskId, tierId) {
	
	jagg.post("/site/blocks/subscription-task/ajax/subscription-task.jag", { action:"setSubscriptionTier",taskId:taskId,tierId:tierId,taskType:"subscription" },
			function (json) {
                if (!json.error) {
                } else {
                    jagg.showLogin();
                }
	}, "json");
}

function loadAllTaskTierDetails() {
	subscriptionTasks = document.getElementsByName("subscription_task");
	//alert(subscriptionTasks[0].value);
	for (var i = 0; i < subscriptionTasks.length; i++) {
		var taskId = subscriptionTasks[i].value;
		var action = "getSubscriptionDetails";
		
		jagg.post("/site/blocks/subscription-task/ajax/subscription-task.jag", {
			action : action,
			taskId : taskId
		}, function(result) {
			if (!result.error) {
				if (result.data != null) {
					setTierDropDownDetails(result);					
					setSubscriptionDescriptions(result);					
				} else {
					jagg.showLogin();
				}
			} else {
				jagg.showLogin();
			}
		}, "json");
	}
}

function setTierDropDownDetails(result){
	var tiers = [];
	var tiersList = document.getElementById('tiers_list_' + result.data.taskId);
	for (var j = 0; j < result.data.tiers.length; j++) {
		tiers[j] = result.data.tiers[j];
		var tierDisplayName = tiers[j].displayName;
		var tierName = tiers[j].name;
		
		if (tierName.toUpperCase() != "DEFAULT") {
			tiersList.options[tiersList.options.length] = new Option(
					tierDisplayName, tierName);
			
			var tierAttributes = tiers[j].attributes;
			for (var k = 0; k < tierAttributes.length; k++) {
				var tierAttributeName = tierAttributes[k].attributeName;
				var tierAttributeValue = tierAttributes[k].attributeValue;
				if(tierAttributeName == "Description"){
					var labelId = "tierAttributeLbl_"+tierName+"_"+result.data.taskId;
					var hiddenFieldId = "tierAttributeHiddenField_"+tierName+"_"+result.data.taskId;
					$("#tierAttributesDiv_"+ result.data.taskId).append("<a id="+labelId+" class='tierRateCard_tooltip_open' style='display: none' onclick='getLblText(this);'>"+tierDisplayName+" Tier</a>");
					$("#tierAttributesDiv_"+ result.data.taskId).append("<input type='hidden' value='"+tierAttributeValue+"' id="+hiddenFieldId+" />");
				}				
			}			
		}
	}
}

function getSelectedTier(dropDownInfo){
	var idParts = dropDownInfo.id.split("_");
	var taskId = idParts[2];
	var selectedTier = $("#"+dropDownInfo.id).val();

	var optionValues = getAllTierOptionValues(dropDownInfo.id);
	var labelId;
	for (var i = 0; i < optionValues.length; i++) {
		labelId = "tierAttributeLbl_"+optionValues[i]+"_"+taskId;
		$("#"+labelId).hide();
	}
	
	labelId = "tierAttributeLbl_"+selectedTier+"_"+taskId;
	$("#"+labelId).show();
}

function getAllTierOptionValues(dropDownId){
	
	var optionValues = [];
	$("#"+dropDownId+" option").each(function()
	{
		optionValues.push($(this).val());
	});
	return optionValues;
}

function setSubscriptionDescriptions(result){
	var nameElement = document.getElementById('desc_name_' + result.data.taskId);
	//alert(">" + JSON.stringify(result));
//	alert(">" + result.data.userName);
	var desElement = document.getElementById('desc_desc_' + result.data.taskId);
	var userElement = document.getElementById('desc_user_' + result.data.taskId);
	var apiElement = document.getElementById('desc_api_' + result.data.taskId);
	nameElement.innerHTML = result.data.applicationName;
	desElement.innerHTML = result.data.applicationDescription;
	userElement.innerHTML = result.data.subscriber;
	apiElement.innerHTML = result.data.apiNameVersion;
}
function showProfileDetailsPopup(anchorElement, username) {
	getProfileData(anchorElement, username);
}

function getProfileData(anchorElement, username) {
	var action = "getProfileData";
	jagg.post("/site/blocks/subscription-task/ajax/subscription-task.jag", {
		action : action,
		profileUser : username
	}, function(result) {
		if (!result.error) {
			setProfileDetailsInPopup(result, username);
		} else {
			$('#my_tooltip').hide();
			jagg.message({
                content:result.errorMsg,
                type:"error"
            });
		}
	}, "json");
}

function setProfileDetailsInPopup(profileData, username) {
	$('#my_tooltip_username').html(username);
	$('#my_tooltip_fname').html(profileData.firstName);
	$('#my_tooltip_lname').html(profileData.lastName);
	$('#my_tooltip_address').html(profileData.address);
	$('#my_tooltip_country').html(profileData.country);
	$('#my_tooltip_org').html(profileData.organization);
	$('#my_tooltip_email').html(profileData.email);
	$('#my_tooltip_phone').html(profileData.telephone);
	$('#my_tooltip_mobile').html(profileData.mobile);
}


$(document).ready(function() {
	// Initialize the plugin
	$('#my_tooltip').popup({
		type: 'tooltip',
		vertical: 'bottom',
        horizontal: 'right',
		transition: '0.3s all 0.1s'
    });	
  });