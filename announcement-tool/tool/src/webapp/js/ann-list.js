$(document).ready(function(){
    // check all hidden save state checkboxes
    $('#undo-last').hide();
    $('#undo-all').hide();
    
    $("input[@name=selectedMembers2]").each(function(){
        this.checked = true;
    });
    // handle changing the order text field
    $("input[id^=index]").change(function(){
        // the new value in the text field
        
        var newVal = this.value;
        if (isNaN(newVal) || newVal > $("input[id^=index]").size()) {
            // alert('newVal:' + newVal +" len: " + $('input[id^=index]').size())
            $('#messageHolder').text('A number smaller than ' + $('input[id^=index]').size() + ' please!');
            $('.orderable-selected').removeClass('orderable-selected')
            $('#messageHolder').removeClass('messageSuccess');
            $('#messageHolder').addClass('messageValidation');
            $('#messageHolder').fadeIn('slow');
		    $("#messageHolder").animate({
		        opacity: 1.0
		    }, 2000)
		    $("#messageHolder").fadeOut('slow');
            $(this).parents('tr').addClass('orderable-selected');
  
            return (null);
            
        }
		
        //what the value was (plucked from a hidden input)
        var oldVal = $(this).siblings('input[id^=holder]').attr('value');
        var inputs = $("input[id^=index]");
     // handle the things that happen after a move
        $('#lastMoveHolderOld').text(oldVal);
        $('#lastMoveHolderNew').text(newVal);
        $('#undo-last').fadeIn('slow');
        $('#undo-last-inact').hide();
        $('#undo-all').fadeIn('slow');
        $('#undo-all-inact').hide();
		      
        
        //insert the row in new location - if new value is 1, insert before, if it is the last possible
        // insert after, otherwise insert before or after depending on if it is going up or down
        if (newVal === '1') {
            $($(this).parents('tr')).insertBefore($(this).parents('tr').siblings('tr').children('td').children('input[value=' + newVal + ']').parents('tr'));
        }
        else 
            if (newVal == inputs.length) {
                $($(this).parents('tr')).insertAfter($(this).parents('tr').siblings('tr').children('td').children('input[value=' + newVal + ']').parents('tr'));
            }
            else {
                if (newVal > oldVal) {
                    $($(this).parents('tr')).insertAfter($(this).parents('tr').siblings('tr').children('td').children('input[value=' + newVal + ']').parents('tr'));
                }
                else {
                    $($(this).parents('tr')).insertBefore($(this).parents('tr').siblings('tr').children('td').children('input[value=' + newVal + ']').parents('tr'));
                }
            }
        
        $(this).parents('tr').addClass('recentMove');
        setTimeout("$('#todo-list tr').removeClass('recentMove')", 1500);
        
     
        
        registerChange('notfluid')
    });
    
    // the standard Fluid initialization
    var opts = {
        selectors: {
            movables: "[id^=myUniquePrefix]"
        },
        listeners: {
            onMove: beforeMove,
            afterMove: registerChange,
        }
    };
    return fluid.reorderList("#todo-list", opts);
});

var undoLast = function(){
    var oldValRet = $('#lastMoveHolderOld').text();
    var newValRet = $('#lastMoveHolderNew').text();
    if (oldValRet === '1') {
        $('#todo-list tr:eq(' + newValRet + ')').insertBefore($('#todo-list tr:eq(1)'));
    }
    else 
        if (newValRet == $('#todo-list tr').size() - 1) {
            $('#todo-list tr:eq(' + newValRet + ')').insertAfter($('#todo-list tr:eq(' + oldValRet + ')'));
        }
        else {
            if (newValRet < oldValRet) {
                $('#todo-list tr:eq(' + newValRet + ')').insertAfter($('#todo-list tr:eq(' + oldValRet + ')'));
			}
            else {
                $('#todo-list tr:eq(' + newValRet + ')').insertBefore($('#todo-list tr:eq(' + oldValRet + ')'));
			}
        }
    $('#undo-last-inact').fadeIn('slow');
    $('#undo-last').hide();
    
    registerChange()
}


// handle things that happen after a move
var registerChange = function(originEvent){
    var rows = $("tbody tr").size();
    $("tr[aria-selected='true']").addClass('recentMove');
    var newVal = 0;
    
    var newVal = ($("tr[aria-selected='true']").prevAll('tr').length + 1);
    
	if (originEvent !='notfluid') {
		$('#lastMoveHolderNew').text(newVal);
	}
    
    
    //show a message, then hide
/*
    $('#messageHolder').removeClass('messageValidation');
    $('#messageHolder').addClass('messageSuccess');
    $('#messageHolder').text('well done! ');
    $("#messageHolder").fadeIn('slow')
    $("#messageHolder").animate({
        opacity: 1.0
    }, 100)

*/    $("#messageHolder").fadeOut('slow');
    
    // change the value of all the text fields (and value holders) to reflect new order
    var inputs = $("input[id^=index]");
    var holderinputs = $("input[id^=holder]");
    for (var i = 0; i < inputs.length; i = i + 1) {
        inputs[i].value = i + 1;
    }
    for (var i = 0; i < holderinputs.length; i = i + 1) {
        holderinputs[i].value = i + 1;
    }
    setTimeout("$('tr').removeClass('recentMove')", 500);
    
}

// handle things that happen before a move
var beforeMove = function(item){
    var oldVal = $(item).children('td').children('input[id^=holder]').attr('value');
    $('#lastMoveHolderOld').text(oldVal);
    $('#undo-last').fadeIn('slow');
    $('#undo-last-inact').hide();
    $('#undo-all').fadeIn('slow');
    $('#undo-all-inact').hide();
    
}
