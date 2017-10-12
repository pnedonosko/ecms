/**
 * Created by toannh on 6/19/15.
 */
(function(gj) {
  var ShareContent = function() {}

  function correctSpacePos(){
    var dropdown = gj(".uiShareDocuments.resizable .spaceSwitcherContainer #DisplayModesDropDown");
    var popup = gj(".uiShareDocuments.resizable .spaceChooserPopup");
    gj(popup).addClass("hide");
    gj(dropdown).bind( "click", function() {
      popup.offset({left:gj(this).offset().left, top:gj(this).offset().top+gj(this).height()-1});
      gj(".spaceChooserPopup").css("width", gj("#SpaceSwitcher").width()-2);
      gj(popup).removeClass("hide");
    });
  }

  ShareContent.prototype.init = function(){
    gj(".uiShareDocuments.resizable .selectbox").addClass("input-medium");
    var sharePopupWindow = gj(".uiShareDocuments.resizable").closest(".UIPopupWindow");
    sharePopupWindow.css("min-width", sharePopupWindow.width());
    sharePopupWindow.find(".uiIconResize").bind("mousedown", function(){
      gj(".uiShareDocuments.resizable .spaceChooserPopup .uiIconClose").trigger("click");
    })
    correctSpacePos();
    //disable shortcuts for pdf.js when a document is opened
    gj("#UIShareDocument").on("keydown", function (evt) {
      evt.stopPropagation();
    });
    gj(".mention-input").on("change", function (event) {
      if (gj("#user").val() != "") {
        gj("#addActionBtn").removeAttr('disabled');
      } else {
        gj("#addActionBtn").attr('disabled','disabled');
      }
    });
    gj("#AccessEntry .btn-toolbar").on("click", function (et) {
      var elId = gj(et.target).closest(".uiActionWithLabel").attr("id").split("-")[1];
      var a = gj("#who .dropdown-menu");
      for (index = 0; index < a.length; ++index) {
        var element = gj("#who .dropdown-menu")[index];
        if (element.parentElement.id.split("-")[1] == elId) continue;
        if (element.style.display == "block") {
          element.style.display = "none";
        }
      }
      var parentTop = et.target.getClientRects()[0].top;
      if (gj(et.target).closest(".uiActionWithLabel").attr("id").indexOf("view") == 0) {
        var element = gj("#canModify-" + elId + " .dropdown-menu")[0];
      } else {
        var element = gj("#canView-" + elId + " .dropdown-menu")[0];
      }
      if (element.style.display == "block") {
        element.style.display = "none";
      } else {
        var top = parentTop - gj(element).closest(".UIPopupWindow")[0].getClientRects()[0].top + 23;
        element.style.top = top + "px";
        if (window.screen.width > 420) {
          element.style.width = "25%";
          element.style.marginLeft = "61%";
        } else {
          element.style.width = "38%";
          element.style.marginLeft = "54%";
        }
        element.style.display = "block";
      }
    });
    gj("#who").on("scroll", function (e) {
      var a = gj("#who .dropdown-menu");
      for (index = 0; index < a.length; ++index) {
        var element = gj("#who .dropdown-menu")[index];
        if (element.style.display == "block") {
          /*var parentTop = element.parentNode.getClientRects()[0].top;
           var top = parentTop - element.closest(".UIPopupWindow").getClientRects()[0].top + 23;
           element.style.top = top + "px";*/
          element.style.display = "none";
        }
      }
    });
    gj("#UIShareDocument").on("scroll", function () {
      var a = gj("#who .dropdown-menu");
      for (index = 0; index < a.length; ++index) {
        var element = gj("#who .dropdown-menu")[index];
        if (element.style.display == "block") {
          /*var parentTop = element.parentNode.getClientRects()[0].top;
           var top = parentTop - element.closest(".UIPopupWindow").getClientRects()[0].top + 23;
           element.style.top = top + "px";*/
          element.style.display = "none";
        }
      }
    });

    gj('.uiShareDocuments.resizable #textAreaInput').suggester({
      type : "mix",
      sourceProviders : ['exo:shareconent'],
      showAtCaret: false,
      renderMenuItem: '<li><div class="avatarSmall" style="display: inline-block;"><img src="${avatar}"></div>${name} (${id})</li>',
      renderItem: '${id}',
      providers: {
        'exo:shareconent': function (query, callback) {
          var url = window.location.protocol + '//' + window.location.host + '/' + eXo.env.portal.rest + '/social/people/getprofile/data.json?search=' + query;
          gj.getJSON(url, function(responseData) {
            responseData = responseData.filter(function(item) {
              return item.name.toLowerCase().indexOf(query.toLowerCase()) > -1;
            });
            callback.call(this, responseData);
          });
        }
      }
    }); // End suggester component

    if (gj("#user").val() == "") {
      gj("#addActionBtn").attr('disabled','disabled');
    } else {
      gj("#addActionBtn").removeAttr('disabled');
    }
  }

  /**
   * Check entry is selected,
   * if selected then enable share button otherwise not enable
   * @param entry
   */
  ShareContent.prototype.checkSelectedEntry = function(entry){
    correctSpacePos();
    if (entry) {
      if ("[]" === entry) {
        gj(".PopupContent .uiActionBorder .btn-primary").attr("disabled", "disabled");
      } else {
        gj(".PopupContent .uiActionBorder .btn-primary").removeAttr("disabled")
      }
    }
  }

  /**
   * Check entry is updated,
   * if the permission of the entry has changed or the entry is removed
   */
  ShareContent.prototype.checkUpdatedEntry = function(){
    gj(".PopupContent .uiActionBorder .btn-primary").removeAttr("disabled");
  }

  eXo.ecm.ShareContent = new ShareContent();
  return {
    ShareContent : eXo.ecm.ShareContent
  };

})(gj);
