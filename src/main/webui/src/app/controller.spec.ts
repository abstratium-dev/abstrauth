import { TestBed } from '@angular/core/testing';

import { Controller } from './controller';

describe('Controller', () => {
  let controller: Controller;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    controller = TestBed.inject(Controller);
  });

  it('should be created', () => {
    expect(controller).toBeTruthy();
  });
});
