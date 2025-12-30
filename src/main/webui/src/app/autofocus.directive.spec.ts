import { AutofocusDirective } from './autofocus.directive';
import { ElementRef } from '@angular/core';

describe('AutofocusDirective', () => {
  it('should create an instance', () => {
    const mockElementRef = new ElementRef(document.createElement('input'));
    const directive = new AutofocusDirective(mockElementRef);
    expect(directive).toBeTruthy();
  });

  it('should focus element after view init', () => {
    const inputElement = document.createElement('input');
    const mockElementRef = new ElementRef(inputElement);
    const directive = new AutofocusDirective(mockElementRef);
    
    spyOn(inputElement, 'focus');
    directive.ngAfterViewInit();
    
    expect(inputElement.focus).toHaveBeenCalled();
  });
});
