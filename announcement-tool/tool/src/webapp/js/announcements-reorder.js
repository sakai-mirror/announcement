$(document).ready(function(){
    //get the initial order TODO - make an  array instead of putting the values in a span
    $('.inactive').remove();
    $('tbody tr').each(function(n){
        $('#lastMoveArrayInit').append($(this).attr('id') + ' ');
        $('#lastMoveArray').append($(this).attr('id') + ' ');
    });
    //remove id from inactive anns, since they cannot be reordered

    // handle changing the order text field
    $("input[id^=index]").change(function(){
        // get existing order
        var that = this;
        preserveStatus();
        // the new value in the text field
        var newVal = this.value;
        if (isNaN(newVal) || newVal > $("input[id^=index]").size()) {
			var failedValidMessage= $('#failedValidMessage').text();
            $('#messageHolder').text(failedValidMessage.replace('#',$('input[id^=index]').size()));
            $('.orderable-selected').removeClass('orderable-selected');
            $('#messageHolder').removeClass('messageSuccess');
            $('#messageHolder').addClass('messageValidation');
            $('#messageHolder').fadeIn('slow');
            $("#messageHolder").animate({
                opacity: 1.0
            }, 2000, function(){
                that.focus();
                that.select();
            });
            $("#messageHolder").fadeOut('slow');
            $(this).parents('tr').addClass('orderable-selected');
            return (null);
        }
        
        //what the value was (plucked from a hidden input)
        var oldVal = $(this).siblings('input[id^=holder]').attr('value');
        var inputs = $("input[id^=index]");
        // handle the things that happen after a move
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
        registerChange('notfluid', $(this).parents('tr'));
    });
    
    // the standard Fluid initialization
    var opts = {
        selectors: {
            movables: "[id^=announcement.orderable]"
        },
        listeners: {
            onBeginMove: preserveStatus,
            afterMove: registerChange
        }
    };
    return fluid.reorderList("#announcement-reorder", opts);
});

var undoLast = function(e){
    var prevOrder;
    var lastMovedT;
    var lastMoved;
    prevOrder = $.trim($('#lastMoveArray').text()).split(" ");
    for (z in prevOrder) {
        thisRow = document.getElementById(prevOrder[z]);
        $(thisRow).appendTo('#announcement-reorder tbody');
    }

    lastMovedT = $.trim($('#lastItemMoved').text());
	lastMoved = $('tr:eq(' + lastMovedT.substr(22) + ')');
	$(lastMoved).addClass('recentMove');
    e.preventDefault();
    registerChange('notfluid', lastMoved);
    $('#undo-last-inact').fadeIn('slow');
    $('#undo-last').hide();


    
};

var undoAll = function(e){
    var initOrder;
    initOrder = $.trim($('#lastMoveArrayInit').text()).split(" ");
	console.log(initOrder);
    for (z in initOrder) {
        thisRow = document.getElementById(initOrder[z]);
        $(thisRow).appendTo('#announcement-reorder tbody');
    }
    e.preventDefault();
	registerChange();
    $('#undo-all').hide();
    $('#undo-all-inact').show();
    $('#undo-last-inact').show();
    $('#undo-last').hide();
    
};


// handle things that happen after a move
var registerChange = function(originEvent, movedEl){
    var rows = $("tbody tr").size();
    if (originEvent !== 'notfluid') {
        movedEl = $("tr[aria-selected='true']");
    }


    $('#lastItemMoved').text($(movedEl).attr('id'));

    $(movedEl).addClass('recentMove');
    var newVal = 0;
    newVal = $((movedEl).prevAll('tr').length + 1);
    // change the value of all the text fields (and value holders) to reflect new order
    var inputsX = $("input[id^=index]");
    var holderinputs = $("input[id^=holder]");
    for (var i = 0; i < inputsX.length; i = i + 1) {
        inputsX[i].value = i + 1;
    }
    for (var x = 0; x < holderinputs.length; x = x + 1) {
        holderinputs[x].value = x + 1;
    }
    
    $('#undo-last').fadeIn('slow');
    $('#undo-last-inact').hide();
    $('#undo-all').fadeIn('slow');
    $('#undo-all-inact').hide();
    $(movedEl).animate({
        opacity: 1.0
    }, 2000, function(){
        $(movedEl).removeClass('recentMove');
   });
};


var preserveStatus = function(item){
    $('#lastMoveArray').text('');
    $('tr').each(function(n){
        if ($(this).attr('id') !== undefined && $(this).attr('id') !== 'undefined_avatar') {
            $('#lastMoveArray').append($(this).attr('id') + ' ');
        }
    });
};
